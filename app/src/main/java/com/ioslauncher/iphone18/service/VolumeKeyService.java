package com.ioslauncher.iphone18.service;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.ioslauncher.iphone18.util.Prefs;

/**
 * Volume keys pakad kar system panel ki jagah iOS pill dikhata hai.
 */
public class VolumeKeyService extends AccessibilityService {

    public static boolean isEnabled(Context ctx) {
        try {
            String flat = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(flat)) return false;
            ComponentName me = new ComponentName(ctx, VolumeKeyService.class);
            return flat.contains(me.flattenToString());
        } catch (Exception e) {
            return false;
        }
    }

    public static void openSettings(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        try {
            int code = event.getKeyCode();
            if (code != KeyEvent.KEYCODE_VOLUME_UP
                    && code != KeyEvent.KEYCODE_VOLUME_DOWN
                    && code != KeyEvent.KEYCODE_VOLUME_MUTE) {
                return false;
            }
            if (!Prefs.volHud(this)) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null) {
                    if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                    } else if (code == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                    } else {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                    }
                }
                OverlayService.showVolumeHud();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
