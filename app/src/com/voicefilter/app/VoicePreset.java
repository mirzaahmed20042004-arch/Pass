package com.voicefilter.app;

/** The two built-in voices (male / female) plus pass-through. */
public final class VoicePreset {

    public static final int MODE_ORIGINAL = 0;
    public static final int MODE_MALE = 1;
    public static final int MODE_FEMALE = 2;

    private VoicePreset() {
    }

    public static VoiceEngine.Params params(int mode, float strength) {
        switch (mode) {
            case MODE_MALE:
                return new VoiceEngine.Params(0.78f, strength, 4.5f, 180f, -4.0f, 3500f);
            case MODE_FEMALE:
                return new VoiceEngine.Params(1.30f, strength, -3.5f, 220f, 4.5f, 4500f);
            default:
                return new VoiceEngine.Params(1.0f, strength, 0f, 200f, 0f, 4000f);
        }
    }

    /** Default pitch percentage for the pitch slider per voice. */
    public static int defaultPitchPct(int mode) {
        switch (mode) {
            case MODE_MALE:
                return 78;
            case MODE_FEMALE:
                return 130;
            default:
                return 100;
        }
    }

    public static String label(int mode) {
        switch (mode) {
            case MODE_MALE:
                return "male";
            case MODE_FEMALE:
                return "female";
            default:
                return "original";
        }
    }
}
