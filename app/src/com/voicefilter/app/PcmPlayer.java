package com.voicefilter.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

/** One-shot PCM player with selectable routing (media vs communication). */
public final class PcmPlayer {

    public interface Done {
        void onDone();
    }

    private AudioTrack track;
    private Thread thread;
    private volatile boolean stop;

    public synchronized void play(final short[] pcm, final int sr, final boolean commRouting, final Done done) {
        stopNow();
        stop = false;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                int minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minBuf <= 0) minBuf = 8192;
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(commRouting ? AudioAttributes.USAGE_VOICE_COMMUNICATION : AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                AudioTrack.Builder b = new AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(sr)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build())
                        .setBufferSizeInBytes(Math.max(minBuf * 2, 16384))
                        .setTransferMode(AudioTrack.MODE_STREAM);
                if (!commRouting && Build.VERSION.SDK_INT >= 26) {
                    b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
                }
                AudioTrack t = b.build();
                track = t;
                if (t.getState() != AudioTrack.STATE_INITIALIZED) {
                    t.release();
                    if (done != null) done.onDone();
                    return;
                }
                t.play();
                int off = 0;
                while (!stop && off < pcm.length) {
                    int n = Math.min(4096, pcm.length - off);
                    t.write(pcm, off, n);
                    off += n;
                }
                if (!stop) {
                    try {
                        t.stop();
                    } catch (IllegalStateException ignored) {
                    }
                }
                t.release();
                track = null;
                if (done != null) done.onDone();
            }
        }, "voicefilter-play");
        thread.start();
    }

    public synchronized void stopNow() {
        stop = true;
        if (thread != null) {
            try {
                thread.join(800);
            } catch (InterruptedException ignored) {
            }
            thread = null;
        }
    }
}
