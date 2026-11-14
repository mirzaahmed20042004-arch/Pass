package com.ioslauncher.iphone18.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Chhoti UI helpers. */
public final class Ui {

    private Ui() {}

    /** Full-screen edge-to-edge + white status icons (iOS jaisa). */
    public static void edgeToEdge(Activity a) {
        try {
            Window w = a.getWindow();
            WindowCompat.setDecorFitsSystemWindows(w, false);
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) {
                w.setNavigationBarContrastEnforced(false);
            }
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
            if (c != null) {
                c.setAppearanceLightStatusBars(false);
                c.setAppearanceLightNavigationBars(false);
            }
        } catch (Exception ignored) {
        }
    }

    /** Settings jaisi light screens ke liye dark status icons. */
    public static void lightStatusBar(Activity a, boolean darkIcons) {
        try {
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(
                    a.getWindow(), a.getWindow().getDecorView());
            if (c != null) c.setAppearanceLightStatusBars(darkIcons);
        } catch (Exception ignored) {
        }
    }

    /** Android 12+ par asli blur, warna kuch nahi (translucent bg hi kaafi). */
    public static void blurBehind(View v, float radius) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && v != null) {
                v.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
            }
        } catch (Exception ignored) {
        }
    }

    public static void hideSystemBars(Activity a) {
        try {
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(
                    a.getWindow(), a.getWindow().getDecorView());
            if (c != null) {
                c.hide(WindowInsetsCompat.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
            a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        } catch (Exception ignored) {
        }
    }

    public static void vibrate(Context ctx) {
        try {
            if (!Prefs.haptics(ctx)) return;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
                    return;
                }
            }
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {
        }
    }

    public static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    public static String timeAgo(long time) {
        long diff = Math.max(0, System.currentTimeMillis() - time);
        long m = diff / 60000;
        if (m < 1) return "now";
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        return (h / 24) + "d ago";
    }
}
