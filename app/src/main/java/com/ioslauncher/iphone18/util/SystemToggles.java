package com.ioslauncher.iphone18.util;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

/** Control Center ke toggles. */
public final class SystemToggles {

    private SystemToggles() {}

    // ---------- Wi-Fi ----------
    public static boolean isWifiOn(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return wm != null && wm.isWifiEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public static void openWifiPanel(Context ctx) {
        try {
            Intent i = new Intent(Settings.Panel.ACTION_WIFI);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }

    // ---------- Bluetooth ----------
    public static boolean isBtOn(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 31
                    && ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter ad = bm != null ? bm.getAdapter() : null;
            return ad != null && ad.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public static void openBtSettings(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    // ---------- Airplane ----------
    public static boolean isAirplaneOn(Context ctx) {
        try {
            return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void openAirplaneSettings(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    // ---------- DND / Focus ----------
    public static boolean dndGranted(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.isNotificationPolicyAccessGranted();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isDndOn(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
        } catch (Exception e) {
            return false;
        }
    }

    public static void toggleDnd(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null || !nm.isNotificationPolicyAccessGranted()) {
                Intent i = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return;
            }
            if (isDndOn(ctx)) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            else nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        } catch (Exception ignored) {
        }
    }

    // ---------- Rotation ----------
    public static boolean canWriteSettings(Context ctx) {
        return Settings.System.canWrite(ctx);
    }

    public static void requestWriteSettings(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    public static boolean isRotationLocked(Context ctx) {
        try {
            return Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 1) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void toggleRotation(Context ctx) {
        try {
            if (!Settings.System.canWrite(ctx)) {
                requestWriteSettings(ctx);
                return;
            }
            boolean locked = isRotationLocked(ctx);
            Settings.System.putInt(ctx.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, locked ? 1 : 0);
        } catch (Exception ignored) {
        }
    }

    // ---------- Flashlight ----------
    private static String flashCameraId(Context ctx) {
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) return id;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean torchState = false;

    public static boolean isTorchOn() {
        return torchState;
    }

    public static boolean toggleTorch(Context ctx) {
        try {
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            String id = flashCameraId(ctx);
            if (cm == null || id == null) return false;
            torchState = !torchState;
            cm.setTorchMode(id, torchState);
            return true;
        } catch (Exception e) {
            torchState = false;
            return false;
        }
    }

    // ---------- Brightness ----------
    public static int brightness(Context ctx) {
        try {
            return Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        } catch (Exception e) {
            return 128;
        }
    }

    public static void setBrightness(Context ctx, int value) {
        try {
            if (!Settings.System.canWrite(ctx)) return;
            int v = Math.max(1, Math.min(255, value));
            Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, v);
        } catch (Exception ignored) {
        }
    }

    // ---------- Volume ----------
    public static AudioManager audio(Context ctx) {
        return (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    }

    public static int volumeMax(Context ctx) {
        try {
            return audio(ctx).getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        } catch (Exception e) {
            return 15;
        }
    }

    public static int volumeNow(Context ctx) {
        try {
            return audio(ctx).getStreamVolume(AudioManager.STREAM_MUSIC);
        } catch (Exception e) {
            return 0;
        }
    }

    public static void setVolume(Context ctx, int value) {
        try {
            audio(ctx).setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
        } catch (Exception ignored) {
        }
    }
}
