package com.ioslauncher.iphone18.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.ioslauncher.iphone18.LockScreenActivity;
import com.ioslauncher.iphone18.R;
import com.ioslauncher.iphone18.util.Prefs;

/** Screen on/off dekh kar iOS lock screen kholta hai. */
public class LockService extends Service {

    private boolean screenOff = false;

    public static void startIfNeeded(Context ctx) {
        try {
            Intent i = new Intent(ctx, LockService.class);
            if (!Prefs.lockScreen(ctx)) {
                ctx.stopService(i);
                return;
            }
            ContextCompat.startForegroundService(ctx, i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                NotificationChannel ch = new NotificationChannel("ios_launcher_bg",
                        "iPhone Launcher", NotificationManager.IMPORTANCE_MIN);
                nm.createNotificationChannel(ch);
            }
            Notification n = new NotificationCompat.Builder(this, "ios_launcher_bg")
                    .setContentTitle("iPhone Launcher")
                    .setContentText("Lock screen active")
                    .setSmallIcon(R.drawable.ic_cc_moon)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setOngoing(true)
                    .build();
            startForeground(1002, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (Exception ignored) {
        }
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                screenOff = true;
            } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                if (screenOff && Prefs.lockScreen(context)) {
                    screenOff = false;
                    try {
                        Intent i = new Intent(context, LockScreenActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        context.startActivity(i);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    };
}
