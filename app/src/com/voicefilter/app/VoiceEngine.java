package com.voicefilter.app;

/**
 * Pure-Java voice morphing engine (no Android dependencies, unit-testable on a desktop JVM).
 *
 * Pipeline: noise gate -> pre-gain -> linear resample (pitch bend) -> WSOLA time-scale
 *           (restore duration, keep new pitch) -> low/high shelf EQ (male/female timbre)
 *           -> feedback echo -> peak normalise.
 */
public final class VoiceEngine {

    public static final class Params {
        public float pitch = 1.0f;        // 1.0 = unchanged, <1 deeper, >1 higher
        public float strength = 1.0f;     // 0..1 blend towards the effect
        public float lowShelfDb = 0f;
        public float lowShelfHz = 200f;
        public float highShelfDb = 0f;
        public float highShelfHz = 4000f;
        public float gainDb = 0f;         // pre-gain (mic boost)
        public float gateDb = -55f;       // noise gate threshold, <= -90 disables
        public int echoMs = 0;            // 0 = off
        public float echoFb = 0.35f;      // echo feedback 0..0.8

        public Params() {
        }

        public Params(float pitch, float strength, float lowShelfDb, float lowShelfHz,
                      float highShelfDb, float highShelfHz) {
            this.pitch = pitch;
            this.strength = strength;
            this.lowShelfDb = lowShelfDb;
            this.lowShelfHz = lowShelfHz;
            this.highShelfDb = highShelfDb;
            this.highShelfHz = highShelfHz;
        }

        public Params copy() {
            Params p = new Params(pitch, strength, lowShelfDb, lowShelfHz, highShelfDb, highShelfHz);
            p.gainDb = gainDb;
            p.gateDb = gateDb;
            p.echoMs = echoMs;
            p.echoFb = echoFb;
            return p;
        }
    }

    private static final int WIN = 2048;
    private static final int HOP = 512;
    private static final int SEARCH = 128;
    private static final int MATCH = 1024;

    private VoiceEngine() {
    }

    public static short[] process(short[] in, int sampleRate, Params p) {
        if (in == null || in.length == 0) {
            return new short[0];
        }
        float strength = clamp(p.strength, 0f, 1f);
        float pitch = 1f + (p.pitch - 1f) * strength;

        float[] x = new float[in.length];
        for (int i = 0; i < in.length; i++) {
            x[i] = in[i] / 32768f;
        }

        if (p.gateDb > -90f) {
            x = noiseGate(x, sampleRate, p.gateDb);
        }
        if (Math.abs(p.gainDb) > 0.05f) {
            float g = (float) Math.pow(10.0, p.gainDb / 20.0);
            for (int i = 0; i < x.length; i++) x[i] *= g;
        }

        if (in.length >= WIN + 2 * SEARCH && Math.abs(pitch - 1f) > 0.002f) {
            x = resample(x, pitch);   // pitch up/down, duration shrinks/grows
            x = wsola(x, pitch);      // stretch back to original duration, pitch preserved
        }

        float loDb = p.lowShelfDb * strength;
        float hiDb = p.highShelfDb * strength;
        if (Math.abs(loDb) > 0.05f) {
            x = biquad(x, shelfCoeffs(sampleRate, p.lowShelfHz, loDb, false));
        }
        if (Math.abs(hiDb) > 0.05f) {
            x = biquad(x, shelfCoeffs(sampleRate, p.highShelfHz, hiDb, true));
        }

        if (p.echoMs > 0) {
            x = echo(x, sampleRate, p.echoMs, clamp(p.echoFb, 0f, 0.8f));
        }

        normalize(x, 0.89f);
        short[] out = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = (short) Math.round(clamp(x[i], -1f, 1f) * 32767f);
        }
        return out;
    }

    /** Soft noise gate: 20 ms RMS windows, gains smoothed per sample to avoid clicks. */
    private static float[] noiseGate(float[] x, int sr, float gateDb) {
        int win = Math.max(64, sr / 50);
        float thr = (float) Math.pow(10.0, gateDb / 20.0);
        int frames = (x.length + win - 1) / win;
        float[] gains = new float[frames + 1];
        for (int f = 0; f < frames; f++) {
            int off = f * win;
            double sum = 0;
            int n = Math.min(win, x.length - off);
            for (int i = 0; i < n; i++) sum += (double) x[off + i] * x[off + i];
            float rms = (float) Math.sqrt(sum / Math.max(1, n));
            // soft knee: full gain 6 dB above threshold, 0.08 floor below
            float g;
            if (rms > thr * 2f) g = 1f;
            else if (rms < thr * 0.5f) g = 0.06f;
            else g = 0.06f + 0.94f * (rms - thr * 0.5f) / (thr * 1.5f);
            gains[f] = g;
        }
        gains[frames] = gains[frames - 1];
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            int f = i / win;
            float t = (i % win) / (float) win;
            y[i] = x[i] * (gains[f] + (gains[f + 1] - gains[f]) * t);
        }
        return y;
    }

    /** Feedback echo: y[i] = x[i] + fb * y[i - d], wet blended at 0.6. */
    private static float[] echo(float[] x, int sr, int ms, float fb) {
        int d = Math.max(1, sr * ms / 1000);
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            float delayed = i >= d ? y[i - d] : 0f;
            y[i] = x[i] + fb * delayed;
        }
        for (int i = 0; i < x.length; i++) {
            y[i] = x[i] * 0.55f + y[i] * 0.45f;
        }
        return y;
    }

    /** out[i] = x[i * ratio]; length becomes len/ratio. ratio>1 => faster & higher pitched. */
    private static float[] resample(float[] x, float ratio) {
        int outLen = Math.max(1, (int) (x.length / ratio));
        float[] out = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            float pos = i * ratio;
            int i0 = (int) pos;
            float frac = pos - i0;
            int i1 = i0 + 1 < x.length ? i0 + 1 : i0;
            out[i] = x[i0] + (x[i1] - x[i0]) * frac;
        }
        return out;
    }

    /** Waveform-similar overlap-add time scaling: output length ~= input length * s. */
    private static float[] wsola(float[] x, float s) {
        int outLen = (int) Math.round(x.length * s);
        if (outLen <= 0) {
            return new float[0];
        }
        int maxIn = Math.max(0, x.length - WIN);
        float[] out = new float[outLen + WIN];
        float[] nrm = new float[outLen + WIN];
        float[] hann = hann(WIN);

        int po = 0;
        boolean first = true;
        while (po < outLen) {
            int base = (int) Math.round(po / s);
            int c = base;
            if (!first) {
                int lo = Math.max(0, base - SEARCH);
                int hi = Math.min(maxIn, base + SEARCH);
                float best = -Float.MAX_VALUE;
                for (int cand = lo; cand <= hi; cand++) {
                    float dot = 0f;
                    for (int i = 0; i < MATCH; i++) {
                        dot += x[cand + i] * out[po + i];
                    }
                    if (dot > best) {
                        best = dot;
                        c = cand;
                    }
                }
            }
            c = clampInt(c, 0, maxIn);
            int limit = Math.min(WIN, out.length - po);
            for (int i = 0; i < limit; i++) {
                out[po + i] += x[c + i] * hann[i];
                nrm[po + i] += hann[i];
            }
            first = false;
            po += HOP;
        }

        float[] res = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            res[i] = out[i] / Math.max(nrm[i], 1e-4f);
        }
        return res;
    }

    private static float[] hann(int n) {
        float[] w = new float[n];
        for (int i = 0; i < n; i++) {
            w[i] = (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (n - 1)));
        }
        return w;
    }

    /** RBJ cookbook shelving filter coefficients, slope ~ 1. */
    private static double[] shelfCoeffs(int sr, float freq, float db, boolean high) {
        double A = Math.pow(10.0, db / 40.0);
        double w0 = 2.0 * Math.PI * Math.min(freq, sr / 2f - 100f) / sr;
        double cosw = Math.cos(w0);
        double sinw = Math.sin(w0);
        double alpha = sinw * 0.7071067811865476;
        double sqA = Math.sqrt(A);
        double b0, b1, b2, a0, a1, a2;
        if (high) {
            b0 = A * ((A + 1) + (A - 1) * cosw + 2 * sqA * alpha);
            b1 = -2 * A * ((A - 1) + (A + 1) * cosw);
            b2 = A * ((A + 1) + (A - 1) * cosw - 2 * sqA * alpha);
            a0 = (A + 1) - (A - 1) * cosw + 2 * sqA * alpha;
            a1 = 2 * ((A - 1) - (A + 1) * cosw);
            a2 = (A + 1) - (A - 1) * cosw - 2 * sqA * alpha;
        } else {
            b0 = A * ((A + 1) - (A - 1) * cosw + 2 * sqA * alpha);
            b1 = 2 * A * ((A - 1) - (A + 1) * cosw);
            b2 = A * ((A + 1) - (A - 1) * cosw - 2 * sqA * alpha);
            a0 = (A + 1) + (A - 1) * cosw + 2 * sqA * alpha;
            a1 = -2 * ((A - 1) + (A + 1) * cosw);
            a2 = (A + 1) + (A - 1) * cosw - 2 * sqA * alpha;
        }
        return new double[]{b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
    }

    private static float[] biquad(float[] x, double[] c) {
        float[] y = new float[x.length];
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        for (int i = 0; i < x.length; i++) {
            double v = c[0] * x[i] + c[1] * x1 + c[2] * x2 - c[3] * y1 - c[4] * y2;
            x2 = x1;
            x1 = x[i];
            y2 = y1;
            y1 = v;
            y[i] = (float) v;
        }
        return y;
    }

    private static void normalize(float[] x, float targetPeak) {
        float peak = 0f;
        for (float v : x) {
            float a = Math.abs(v);
            if (a > peak) peak = a;
        }
        if (peak > 1e-5f) {
            float g = targetPeak / peak;
            for (int i = 0; i < x.length; i++) x[i] *= g;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
