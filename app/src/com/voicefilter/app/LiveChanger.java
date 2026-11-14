package com.voicefilter.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

/**
 * Real-time morphing: mic chunks are processed block by block and streamed to a
 * low-latency AudioTrack. A short linear cross-fade hides block boundaries.
 */
public final class LiveChanger {

    public interface LevelListener {
        void onLevel(float rms);

        void onLatency(long blockMillis);
    }

    private static final int BLOCK = 8192;
    private static final int FADE = 128;

    private final LevelListener levelListener;
    private volatile VoiceEngine.Params params = new VoiceEngine.Params();
    private AudioTrack track;
    private RecorderEngine recorder;
    private final short[] pending = new short[BLOCK * 2];
    private int pendingCount = 0;
    private float[] lastTail = new float[FADE];
    private volatile boolean active;

    public LiveChanger(LevelListener levelListener) {
        this.levelListener = levelListener;
    }

    public void setParams(VoiceEngine.Params p) {
        this.params = p.copy();
    }

    public synchronized void start() {
        if (active) return;
        active = true;
        pendingCount = 0;
        lastTail = new float[FADE];

        int minBuf = AudioTrack.getMinBufferSize(RecorderEngine.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) minBuf = 8192;
        AudioTrack.Builder builder = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(RecorderEngine.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(Math.max(minBuf * 2, BLOCK * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE);
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
        }
        AudioTrack t = builder.build();
        track = t;
        t.play();

        recorder = RecorderEngine.forLive(new RecorderEngine.Listener() {
            @Override
            public void onLevel(float rms) {
                levelListener.onLevel(rms);
            }

            @Override
            public void onChunk(short[] samples, int count) {
                feed(samples, count);
            }

            @Override
            public void onStopped(short[] pcm, int sampleRate, long millis) {
            }
        });
        recorder.start();
    }

    public synchronized void stop() {
        if (!active) return;
        active = false;
        if (recorder != null) {
            recorder.stop();
            recorder = null;
        }
        if (track != null) {
            try {
                track.stop();
                track.release();
            } catch (Throwable ignored) {
            }
            track = null;
        }
    }

    private synchronized void feed(short[] samples, int count) {
        if (!active) return;
        int src = 0;
        while (src < count) {
            int n = Math.min(count - src, BLOCK - pendingCount);
            System.arraycopy(samples, src, pending, pendingCount, n);
            pendingCount += n;
            src += n;
            if (pendingCount >= BLOCK) {
                emitBlock();
            }
        }
    }

    private void emitBlock() {
        short[] block = new short[pendingCount];
        System.arraycopy(pending, 0, block, 0, pendingCount);
        pendingCount = 0;
        long t0 = System.currentTimeMillis();
        short[] out = VoiceEngine.process(block, RecorderEngine.SAMPLE_RATE, params);
        levelListener.onLatency(System.currentTimeMillis() - t0);

        // linear cross-fade over the first FADE samples against the previous block tail
        float[] cur = new float[out.length];
        for (int i = 0; i < out.length; i++) cur[i] = out[i] / 32768f;
        int fade = Math.min(FADE, out.length);
        for (int i = 0; i < fade; i++) {
            float g = (float) i / fade;
            cur[i] = lastTail[i] * (1f - g) + cur[i] * g;
        }
        float[] newTail = new float[FADE];
        int tailStart = Math.max(0, out.length - FADE);
        for (int i = 0; i < FADE && tailStart + i < out.length; i++) {
            newTail[i] = cur[tailStart + i];
        }
        lastTail = newTail;

        short[] pcm = new short[out.length];
        for (int i = 0; i < out.length; i++) {
            pcm[i] = (short) Math.round(Math.max(-1f, Math.min(1f, cur[i])) * 32767f);
        }
        if (track != null) {
            track.write(pcm, 0, pcm.length);
        }
    }
}
