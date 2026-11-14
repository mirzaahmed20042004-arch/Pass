package com.voicefilter.app;

import android.content.Context;
import android.media.AudioManager;

/**
 * Routes broadcast audio to the loudspeaker even when a headset is plugged in,
 * so the game microphone (on the phone) always hears the filtered clip.
 * Uses only public AudioManager communication-mode routing - no injection.
 */
public final class SpeakerRouter {

    private SpeakerRouter() {
    }

    public static void engage(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        am.setSpeakerphoneOn(true);
    }

    public static void release(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        am.setSpeakerphoneOn(false);
        am.setMode(AudioManager.MODE_NORMAL);
    }
}
