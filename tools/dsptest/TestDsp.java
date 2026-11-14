import com.voicefilter.app.VoiceEngine;

/** Desktop-JVM unit test for the morphing engine: pitch must land where the preset says. */
public class TestDsp {

    private static int failures = 0;

    public static void main(String[] args) {
        int sr = 44100;
        double f0 = 160.0;
        int n = sr * 2;
        short[] in = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) sr;
            double v = 0;
            for (int h = 1; h <= 6; h++) {
                v += Math.sin(2 * Math.PI * f0 * h * t) / h;
            }
            v += 0.05 * Math.sin(2 * Math.PI * 2300 * t);
            in[i] = (short) (v * 4000);
        }

        double inPitch = estimatePitch(in, sr);
        System.out.printf("input pitch estimate: %.1f Hz (expected ~%.0f)%n", inPitch, f0);
        check("input pitch detector", inPitch, f0, 0.05);

        VoiceEngine.Params male = new VoiceEngine.Params(0.78f, 1f, 4.5f, 180f, -4f, 3500f);
        VoiceEngine.Params female = new VoiceEngine.Params(1.30f, 1f, -3.5f, 220f, 4.5f, 4500f);

        long t0 = System.currentTimeMillis();
        short[] m = VoiceEngine.process(in, sr, male);
        long t1 = System.currentTimeMillis();
        short[] f = VoiceEngine.process(in, sr, female);
        long t2 = System.currentTimeMillis();
        System.out.printf("male processed in %d ms, female in %d ms (%.1f s of audio)%n",
                t1 - t0, t2 - t1, n / (double) sr);

        double mp = estimatePitch(m, sr);
        double fp = estimatePitch(f, sr);
        System.out.printf("male pitch: %.1f Hz (expected %.1f)%n", mp, f0 * 0.78);
        System.out.printf("female pitch: %.1f Hz (expected %.1f)%n", fp, f0 * 1.30);
        check("male pitch", mp, f0 * 0.78, 0.08);
        check("female pitch", fp, f0 * 1.30, 0.08);

        checkLen("male duration", m.length, n);
        checkLen("female duration", f.length, n);

        short[] orig = VoiceEngine.process(in, sr, new VoiceEngine.Params(1f, 1f, 0, 200, 0, 4000));
        double op = estimatePitch(orig, sr);
        check("original passthrough pitch", op, f0, 0.05);

        if (failures > 0) {
            System.out.println("DSP TEST FAILED: " + failures + " checks");
            System.exit(1);
        }
        System.out.println("DSP TEST PASSED");
    }

    private static void check(String what, double got, double want, double tol) {
        if (Math.abs(got - want) > want * tol) {
            System.out.println("  FAIL " + what + ": got " + got + " want " + want);
            failures++;
        } else {
            System.out.println("  ok   " + what);
        }
    }

    private static void checkLen(String what, int got, int want) {
        if (Math.abs(got - want) > want * 0.03) {
            System.out.println("  FAIL " + what + ": got " + got + " want " + want);
            failures++;
        } else {
            System.out.println("  ok   " + what + " (" + got + " samples)");
        }
    }

    /** YIN-style pitch estimate (difference function + CMN), robust against octave errors. */
    private static double estimatePitch(short[] x, int sr) {
        int minLag = sr / 400;
        int maxLag = sr / 60;
        int W = 8000;
        int windows = Math.max(1, Math.min(4, (x.length - maxLag) / W));
        double[] acc = new double[maxLag + 1];
        for (int w = 0; w < windows; w++) {
            int base = w * W;
            for (int lag = minLag; lag <= maxLag; lag++) {
                double sum = 0;
                for (int i = 0; i < W - lag; i += 2) {
                    double d = (x[base + i] - x[base + i + lag]) / 32768.0;
                    sum += d * d;
                }
                acc[lag] += sum;
            }
        }
        double[] cmn = new double[maxLag + 1];
        double run = 0;
        cmn[0] = 1;
        for (int lag = 1; lag <= maxLag; lag++) {
            run += acc[lag];
            cmn[lag] = run > 1e-12 ? acc[lag] * lag / run : 1;
        }
        int bestLag = -1;
        for (int lag = minLag; lag <= maxLag; lag++) {
            if (cmn[lag] < 0.12) {
                bestLag = lag;
                break;
            }
        }
        if (bestLag < 0) {
            double best = Double.MAX_VALUE;
            for (int lag = minLag; lag <= maxLag; lag++) {
                if (cmn[lag] < best) {
                    best = cmn[lag];
                    bestLag = lag;
                }
            }
        }
        // parabolic interpolation around the dip
        if (bestLag > minLag && bestLag < maxLag) {
            double a = cmn[bestLag - 1], b = cmn[bestLag], c = cmn[bestLag + 1];
            double denom = (a - 2 * b + c);
            if (Math.abs(denom) > 1e-12) {
                double shift = 0.5 * (a - c) / denom;
                return sr / (bestLag + shift);
            }
        }
        return bestLag > 0 ? sr / (double) bestLag : -1;
    }
}
