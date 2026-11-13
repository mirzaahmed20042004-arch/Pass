package com.ultronai.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

/** A user-started, draggable shortcut. Long press captures one accessibility snapshot. */
public class HudService extends Service {
    private static final String CHANNEL = "ultron_hud";
    private static final String STOP = "com.ultronai.assistant.STOP_HUD";
    private static volatile boolean running;
    private WindowManager windowManager;
    private View bubble;
    private WindowManager.LayoutParams params;
    private final Handler handler = new Handler(Looper.getMainLooper());
    static boolean isRunning() { return running; }

    @Override public void onCreate() {
        super.onCreate();
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL, "Floating HUD", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Shows when the Ultron floating shortcut is active.");
                getSystemService(NotificationManager.class).createNotificationChannel(channel);
            }
            Notification notification = notification();
            if (Build.VERSION.SDK_INT >= 34) startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(101, notification);
            createBubble();
            running = true;
        } catch (Exception ex) {
            android.util.Log.e("UltronHUD", "Failed to start the floating HUD", ex);
            Toast.makeText(this, "Could not display the HUD. Check overlay permission.", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        return START_NOT_STICKY;
    }
    private Notification notification() {
        int immutable = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openIntent = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | immutable);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, new Intent(this, HudService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | immutable);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_notification).setContentTitle("Ultron HUD is on")
                .setContentText("Tap to open Ultron. Long-press the bubble to read screen labels.")
                .setContentIntent(openIntent).setOngoing(true).addAction(R.drawable.ic_notification, "Turn off", stopIntent).build();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void createBubble() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        FrameLayout frame = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.rgb(6, 24, 37)); background.setCornerRadius(dp(19)); background.setStroke(dp(2), Color.rgb(94, 224, 225));
        frame.setBackground(background); frame.setElevation(dp(8));
        ImageView image = new ImageView(this); image.setImageResource(R.mipmap.ic_launcher); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER);
        frame.addView(image, imageParams); frame.setContentDescription("Ultron Ai floating HUD. Tap to open, long press to read the screen, or drag to move.");
        frame.setOnClickListener(v -> openApp());
        frame.setOnLongClickListener(v -> { MainActivity.queueSnapshot(AssistService.snapshot()); openApp(); return true; });
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(dp(54), dp(54), type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT; params.x = Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(65)); params.y = dp(170);
        frame.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY; float downX, downY; boolean moved, longPressed;
            final Runnable longPress = () -> { if (!moved) { longPressed = true; frame.performLongClick(); } };
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x; startY = params.y; downX = event.getRawX(); downY = event.getRawY(); moved = false; longPressed = false;
                        handler.postDelayed(longPress, 650); return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(event.getRawX() - downX), dy = Math.round(event.getRawY() - downY);
                        if (Math.abs(dx) > dp(7) || Math.abs(dy) > dp(7)) { moved = true; handler.removeCallbacks(longPress); }
                        if (moved) {
                            int maxX = Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(54));
                            int maxY = Math.max(0, getResources().getDisplayMetrics().heightPixels - dp(54));
                            params.x = Math.max(0, Math.min(maxX, startX + dx)); params.y = Math.max(0, Math.min(maxY, startY + dy));
                            try { windowManager.updateViewLayout(frame, params); } catch (Exception ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(longPress); if (!moved && !longPressed) frame.performClick(); return true;
                    case MotionEvent.ACTION_CANCEL: handler.removeCallbacks(longPress); return true;
                    default: return false;
                }
            }
        });
        windowManager.addView(frame, params); bubble = frame;
    }
    private void openApp() {
        try { startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); }
        catch (Exception ex) { Toast.makeText(this, "Open Ultron Ai to continue.", Toast.LENGTH_SHORT).show(); }
    }
    @Override public void onDestroy() {
        running = false; handler.removeCallbacksAndMessages(null);
        if (bubble != null && windowManager != null) { try { windowManager.removeView(bubble); } catch (Exception ignored) { } }
        bubble = null; super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
