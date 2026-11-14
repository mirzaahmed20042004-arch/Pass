package com.ioslauncher.iphone18.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.ioslauncher.iphone18.ControlCenterActivity;
import com.ioslauncher.iphone18.LauncherActivity;
import com.ioslauncher.iphone18.LockScreenActivity;
import com.ioslauncher.iphone18.NotificationCenterActivity;
import com.ioslauncher.iphone18.R;
import com.ioslauncher.iphone18.SettingsActivity;
import com.ioslauncher.iphone18.util.Prefs;
import com.ioslauncher.iphone18.util.SystemToggles;
import com.ioslauncher.iphone18.util.Ui;

/**
 * Floating iOS features: top-edge gestures, AssistiveTouch, volume HUD, island alerts.
 */
public class OverlayService extends Service {

    private static final String CHANNEL = "ios_launcher_bg";
    private static OverlayService instance;

    private WindowManager wm;
    private View ccHandle;
    private View ncHandle;
    private View assistive;
    private View hud;
    private View island;
    private View assistMenu;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int lastVolume = -1;

    public static void startIfNeeded(Context ctx) {
        try {
            boolean need = Prefs.ccGesture(ctx) || Prefs.ncGesture(ctx) || Prefs.assistive(ctx)
                    || Prefs.volHud(ctx) || Prefs.island(ctx);
            Intent i = new Intent(ctx, OverlayService.class);
            if (!need) {
                ctx.stopService(i);
                return;
            }
            if (!Settings.canDrawOverlays(ctx)) return;
            ContextCompat.startForegroundService(ctx, i);
        } catch (Exception ignored) {
        }
    }

    public static void showVolumeHud() {
        try {
            if (instance != null) instance.showHud();
        } catch (Exception ignored) {
        }
    }

    public static void refreshViews() {
        try {
            if (instance != null) instance.rebuild();
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
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        startQuietForeground();
        rebuild();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(powerReceiver, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        rebuild();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(powerReceiver);
        } catch (Exception ignored) {
        }
        removeAll();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private void startQuietForeground() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "iPhone Launcher", NotificationManager.IMPORTANCE_MIN);
                nm.createNotificationChannel(ch);
            }
            Notification n = new NotificationCompat.Builder(this, CHANNEL)
                    .setContentTitle("iPhone Launcher")
                    .setContentText("iOS features active")
                    .setSmallIcon(R.drawable.ic_cc_moon)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setOngoing(true)
                    .build();
            startForeground(1001, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (Exception ignored) {
        }
    }

    // ---------------- views ----------------

    private WindowManager.LayoutParams baseParams(int w, int h, int gravity) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity = gravity;
        return p;
    }

    private void rebuild() {
        removeAll();
        if (wm == null) return;
        try {
            int screenW = getResources().getDisplayMetrics().widthPixels;
            if (Prefs.ncGesture(this)) {
                ncHandle = new View(this);
                WindowManager.LayoutParams p = baseParams((int) (screenW * 0.55), Ui.dp(this, 26),
                        Gravity.TOP | Gravity.START);
                p.y = 0;
                final float[] down = new float[2];
                final boolean[] fired = {false};
                ncHandle.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent e) {
                        if (e.getAction() == MotionEvent.ACTION_DOWN) {
                            down[0] = e.getRawX();
                            down[1] = e.getRawY();
                            fired[0] = false;
                            return true;
                        }
                        if (e.getAction() == MotionEvent.ACTION_MOVE && !fired[0]) {
                            float dy = e.getRawY() - down[1];
                            float dx = e.getRawX() - down[0];
                            if (dy > Ui.dp(OverlayService.this, 40) && Math.abs(dx) < Ui.dp(OverlayService.this, 140)) {
                                fired[0] = true;
                                openCenter(false);
                                return true;
                            }
                        }
                        if (e.getAction() == MotionEvent.ACTION_UP && !fired[0]) {
                            openCenter(false);
                            return true;
                        }
                        return true;
                    }
                });
                wm.addView(ncHandle, p);
            }
            if (Prefs.ccGesture(this)) {
                ccHandle = new View(this);
                WindowManager.LayoutParams p = baseParams((int) (screenW * 0.45), Ui.dp(this, 26),
                        Gravity.TOP | Gravity.END);
                p.y = 0;
                final float[] down = new float[2];
                final boolean[] fired = {false};
                ccHandle.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent e) {
                        if (e.getAction() == MotionEvent.ACTION_DOWN) {
                            down[0] = e.getRawX();
                            down[1] = e.getRawY();
                            fired[0] = false;
                            return true;
                        }
                        if (e.getAction() == MotionEvent.ACTION_MOVE && !fired[0]) {
                            float dy = e.getRawY() - down[1];
                            float dx = e.getRawX() - down[0];
                            if (dy > Ui.dp(OverlayService.this, 40) && Math.abs(dx) < Ui.dp(OverlayService.this, 140)) {
                                fired[0] = true;
                                openCenter(true);
                                return true;
                            }
                        }
                        if (e.getAction() == MotionEvent.ACTION_UP && !fired[0]) {
                            openCenter(true);
                            return true;
                        }
                        return true;
                    }
                });
                wm.addView(ccHandle, p);
            }
            if (Prefs.assistive(this)) addAssistive();
            if (Prefs.volHud(this)) addHud();
            if (Prefs.island(this)) addIsland();
        } catch (Exception ignored) {
        }
    }

    private void openCenter(boolean cc) {
        try {
            Ui.vibrate(this);
            Intent i = new Intent(this, cc ? ControlCenterActivity.class : NotificationCenterActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    // ---------------- AssistiveTouch ----------------

    private WindowManager.LayoutParams assistParams;

    private void addAssistive() {
        assistive = LayoutInflater.from(this).inflate(R.layout.overlay_assistive, null);
        assistParams = baseParams(Ui.dp(this, 56), Ui.dp(this, 56), Gravity.TOP | Gravity.START);
        assistParams.x = Ui.dp(this, 12);
        assistParams.y = getResources().getDisplayMetrics().heightPixels / 2;
        final float[] downRaw = new float[2];
        final int[] downXY = new int[2];
        final boolean[] moved = {false};
        assistive.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downRaw[0] = e.getRawX();
                    downRaw[1] = e.getRawY();
                    downXY[0] = assistParams.x;
                    downXY[1] = assistParams.y;
                    moved[0] = false;
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    int dx = (int) (e.getRawX() - downRaw[0]);
                    int dy = (int) (e.getRawY() - downRaw[1]);
                    if (Math.abs(dx) + Math.abs(dy) > Ui.dp(OverlayService.this, 10)) moved[0] = true;
                    if (moved[0]) {
                        assistParams.x = downXY[0] + dx;
                        assistParams.y = downXY[1] + dy;
                        try {
                            wm.updateViewLayout(assistive, assistParams);
                        } catch (Exception ignored) {
                        }
                    }
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (!moved[0]) toggleAssistMenu();
                    return true;
                }
                return true;
            }
        });
        try {
            wm.addView(assistive, assistParams);
        } catch (Exception ignored) {
        }
    }

    private void toggleAssistMenu() {
        Ui.vibrate(this);
        if (assistMenu != null) {
            try {
                wm.removeView(assistMenu);
            } catch (Exception ignored) {
            }
            assistMenu = null;
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_card);
        int pad = Ui.dp(this, 10);
        box.setPadding(pad, pad, pad, pad);
        addMenuBtn(box, "Home", LauncherActivity.class);
        addMenuBtn(box, "Control Center", ControlCenterActivity.class);
        addMenuBtn(box, "Notifications", NotificationCenterActivity.class);
        addMenuBtn(box, "Lock Screen", LockScreenActivity.class);
        addMenuBtn(box, "Settings", SettingsActivity.class);
        assistMenu = box;
        WindowManager.LayoutParams p = baseParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        int sw = getResources().getDisplayMetrics().widthPixels;
        p.x = Math.min(Math.max(assistParams.x - Ui.dp(this, 60), 8), sw - Ui.dp(this, 220));
        p.y = Math.max(assistParams.y - Ui.dp(this, 120), Ui.dp(this, 60));
        try {
            wm.addView(assistMenu, p);
        } catch (Exception ignored) {
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (assistMenu != null) {
                    try {
                        wm.removeView(assistMenu);
                    } catch (Exception ignored) {
                    }
                    assistMenu = null;
                }
            }
        }, 6000);
    }

    private void addMenuBtn(LinearLayout box, String label, final Class<?> cls) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundColor(0x00000000);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (assistMenu != null) {
                    try {
                        wm.removeView(assistMenu);
                    } catch (Exception ignored) {
                    }
                    assistMenu = null;
                }
                try {
                    Intent i = new Intent(OverlayService.this, cls);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception ignored) {
                }
            }
        });
        box.addView(b);
    }

    // ---------------- Volume HUD ----------------

    private void addHud() {
        hud = LayoutInflater.from(this).inflate(R.layout.overlay_volume_hud, null);
        WindowManager.LayoutParams p = baseParams(Ui.dp(this, 44), Ui.dp(this, 180),
                Gravity.START | Gravity.CENTER_VERTICAL);
        p.x = Ui.dp(this, 6);
        hud.setVisibility(View.GONE);
        try {
            wm.addView(hud, p);
        } catch (Exception ignored) {
        }
        lastVolume = SystemToggles.volumeNow(this);
    }

    private final Runnable hideHud = new Runnable() {
        @Override
        public void run() {
            if (hud != null) hud.setVisibility(View.GONE);
        }
    };

    private void showHud() {
        if (hud == null || !Prefs.volHud(this)) return;
        int max = Math.max(1, SystemToggles.volumeMax(this));
        final int now = SystemToggles.volumeNow(this);
        lastVolume = now;
        final View fill = hud.findViewById(R.id.hud_fill);
        if (fill != null) {
            final float frac = Math.max(0.03f, now / (float) max);
            hud.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        int h = hud.getHeight() - Ui.dp(OverlayService.this, 12);
                        if (h < 10) h = Ui.dp(OverlayService.this, 168);
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                        lp.height = Math.max(Ui.dp(OverlayService.this, 6), (int) (h * frac));
                        lp.gravity = Gravity.BOTTOM;
                        fill.setLayoutParams(lp);
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        hud.setVisibility(View.VISIBLE);
        hud.setAlpha(0f);
        hud.animate().alpha(1f).setDuration(120).start();
        handler.removeCallbacks(hideHud);
        handler.postDelayed(hideHud, 1300);
    }

    // ---------------- Island ----------------

    private void addIsland() {
        island = LayoutInflater.from(this).inflate(R.layout.overlay_island, null);
        WindowManager.LayoutParams p = baseParams(Ui.dp(this, 110), Ui.dp(this, 30),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        p.y = Ui.dp(this, 8);
        island.setVisibility(View.GONE);
        try {
            wm.addView(island, p);
        } catch (Exception ignored) {
        }
    }

    private void flashIsland(String text) {
        if (island == null || !Prefs.island(this)) return;
        TextView tv = island.findViewById(R.id.island_text);
        if (tv != null) {
            tv.setText(text);
            tv.setVisibility(View.VISIBLE);
        }
        island.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideIsland);
        handler.postDelayed(hideIsland, 4000);
    }

    private final Runnable hideIsland = new Runnable() {
        @Override
        public void run() {
            if (island != null) island.setVisibility(View.GONE);
        }
    };

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String a = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(a)) flashIsland("⚡ Charging");
            else if (Intent.ACTION_POWER_DISCONNECTED.equals(a)) flashIsland("Charger removed");
        }
    };

    private void removeAll() {
        removeView(ncHandle);
        removeView(ccHandle);
        removeView(assistive);
        removeView(hud);
        removeView(island);
        removeView(assistMenu);
        ncHandle = null;
        ccHandle = null;
        assistive = null;
        hud = null;
        island = null;
        assistMenu = null;
    }

    private void removeView(View v) {
        if (v != null && wm != null) {
            try {
                wm.removeView(v);
            } catch (Exception ignored) {
            }
        }
    }
}
