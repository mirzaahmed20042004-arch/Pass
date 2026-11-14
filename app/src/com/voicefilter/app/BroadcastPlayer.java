package com.voicefilter.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/**
 * Shared broadcast flow: countdown on the caller's status sink, then the clip is
 * played through the loudspeaker (optionally bypassing headset routing) so any
 * nearby microphone - including a game's - picks it up.
 */
public final class BroadcastPlayer {

    public interface Status {
        void onStatus(String text);
    }

    private static final PcmPlayer player = new PcmPlayer();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Runnable pendingCountdown;
    private static boolean routed;

    private BroadcastPlayer() {
    }

    public static boolean forceSpeaker(Context context) {
        SharedPreferences p = context.getSharedPreferences("voicefilter", Context.MODE_PRIVATE);
        return p.getBoolean("force_speaker", true);
    }

    public static void cancel() {
        if (pendingCountdown != null) {
            main.removeCallbacks(pendingCountdown);
            pendingCountdown = null;
        }
        player.stopNow();
        if (routed) {
            routed = false;
        }
    }

    public static void broadcast(final Context context, final ClipLibrary.Clip clip, final Status status) {
        cancel();
        final boolean force = forceSpeaker(context);
        final int[] step = {3};
        if (status != null) status.onStatus(context.getString(R.string.countdown, step[0]));
        pendingCountdown = new Runnable() {
            @Override
            public void run() {
                step[0]--;
                if (step[0] > 0) {
                    if (status != null) status.onStatus(context.getString(R.string.countdown, step[0]));
                    main.postDelayed(this, 1000);
                } else {
                    pendingCountdown = null;
                    play(context, clip, force, status);
                }
            }
        };
        main.postDelayed(pendingCountdown, 1000);
    }

    private static void play(final Context context, ClipLibrary.Clip clip, boolean force, final Status status) {
        try {
            final WavFile.Data data = WavFile.read(new File(clip.file.getAbsolutePath()));
            if (force) {
                SpeakerRouter.engage(context);
                routed = true;
            }
            if (status != null) status.onStatus(context.getString(R.string.broadcasting));
            player.play(data.pcm, data.sampleRate, force, new PcmPlayer.Done() {
                @Override
                public void onDone() {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (routed) {
                                SpeakerRouter.release(context);
                                routed = false;
                            }
                            if (status != null) status.onStatus("");
                        }
                    });
                }
            });
        } catch (Exception e) {
            if (routed) {
                SpeakerRouter.release(context);
                routed = false;
            }
            if (status != null) status.onStatus("Broadcast failed: " + e.getMessage());
        }
    }
}
