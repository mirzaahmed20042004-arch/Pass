package com.voicefilter.app;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.util.ArrayList;

/**
 * Microphone capture on a background thread. Emits live level updates and,
 * optionally, raw chunks (used by the live changer); delivers the full take on stop.
 */
public final class RecorderEngine {

    public interface Listener {
        void onLevel(float rms);

        void onChunk(short[] samples, int count);

        void onStopped(short[] pcm, int sampleRate, long millis);
    }

    public static final int SAMPLE_RATE = 44100;

    private final int source;
    private final Listener listener;
    private Thread thread;
    private volatile boolean running;

    public RecorderEngine(int audioSource, Listener listener) {
        this.source = audioSource;
        this.listener = listener;
    }

    public static RecorderEngine forStudio(Listener l) {
        return new RecorderEngine(MediaRecorder.AudioSource.MIC, l);
    }

    public static RecorderEngine forLive(Listener l) {
        return new RecorderEngine(MediaRecorder.AudioSource.VOICE_COMMUNICATION, l);
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                captureLoop();
            }
        }, "voicemorph-rec");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(1500);
            } catch (InterruptedException ignored) {
            }
            thread = null;
        }
    }

    private void captureLoop() {
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) minBuf = 4096;
        int bufSize = Math.max(minBuf * 2, SAMPLE_RATE / 5);
        AudioRecord rec = null;
        try {
            rec = new AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                rec.release();
                listener.onStopped(new short[0], SAMPLE_RATE, 0);
                return;
            }
            rec.startRecording();
            short[] buf = new short[2048];
            ArrayList<short[]> chunks = new ArrayList<short[]>();
            ArrayList<Integer> counts = new ArrayList<Integer>();
            int total = 0;
            long t0 = System.currentTimeMillis();
            while (running) {
                int n = rec.read(buf, 0, buf.length);
                if (n <= 0) break;
                float sum = 0f;
                for (int i = 0; i < n; i++) {
                    float v = buf[i] / 32768f;
                    sum += v * v;
                }
                listener.onLevel((float) Math.sqrt(sum / n));
                short[] copy = new short[n];
                System.arraycopy(buf, 0, copy, 0, n);
                listener.onChunk(copy, n);
                chunks.add(copy);
                counts.add(n);
                total += n;
            }
            long millis = System.currentTimeMillis() - t0;
            try {
                rec.stop();
            } catch (IllegalStateException ignored) {
            }
            rec.release();
            short[] all = new short[total];
            int off = 0;
            for (int i = 0; i < chunks.size(); i++) {
                System.arraycopy(chunks.get(i), 0, all, off, counts.get(i));
                off += counts.get(i);
            }
            listener.onStopped(all, SAMPLE_RATE, millis);
        } catch (Throwable t) {
            if (rec != null) {
                try {
                    rec.release();
                } catch (Throwable ignored) {
                }
            }
            listener.onStopped(new short[0], SAMPLE_RATE, 0);
        }
    }
}
