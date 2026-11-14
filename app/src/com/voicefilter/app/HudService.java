package com.voicefilter.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.List;

/**
 * Floating game HUD: a draggable neon bubble that stays on top of PUBG and every
 * other app (SYSTEM_ALERT_WINDOW overlay + foreground service). Blue = male voice,
 * pink = female voice. Tap toggles the live monitor, long-press opens the panel.
 */
public class HudService extends Service {

    private static final String CHANNEL = "voicefilter_hud";
    private static final int NOTIF_ID = 71;

    private static HudService inst;

    public static boolean running() {
        return inst != null;
    }

    public static HudService get() {
        return inst;
    }

    public static void start(Context context) {
        Intent i = new Intent(context, HudService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(i);
        } else {
            context.startService(i);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, HudService.class));
    }

    private WindowManager wm;
    private View root;
    private View bubble;
    private View panel;
    private TextView monitorBtn;
    private TextView maleBtn;
    private TextView femaleBtn;
    private WindowManager.LayoutParams wlp;
    private LiveChanger live;
    private SharedPreferences prefs;
    private int voice = VoicePreset.MODE_MALE;

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        prefs = getSharedPreferences("voicefilter", MODE_PRIVATE);
        voice = prefs.getInt("hud_voice", VoicePreset.MODE_MALE);
        createChannel();
        startForeground(NOTIF_ID, buildNotification());

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        root = LayoutInflater.from(this).inflate(R.layout.hud_overlay, null);
        bubble = root.findViewById(R.id.hud_bubble);
        panel = root.findViewById(R.id.hud_panel);
        monitorBtn = (TextView) root.findViewById(R.id.hud_monitor);
        maleBtn = (TextView) root.findViewById(R.id.hud_male);
        femaleBtn = (TextView) root.findViewById(R.id.hud_female);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        wlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        wlp.gravity = Gravity.TOP | Gravity.START;
        wlp.x = 24;
        wlp.y = 320;
        wm.addView(root, wlp);

        maleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setVoice(VoicePreset.MODE_MALE);
            }
        });
        femaleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setVoice(VoicePreset.MODE_FEMALE);
            }
        });
        monitorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMonitor(live == null);
            }
        });
        root.findViewById(R.id.hud_broadcast).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                broadcastLast();
            }
        });
        root.findViewById(R.id.hud_hide).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                panel.setVisibility(View.GONE);
            }
        });
        bubble.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                return true;
            }
        });
        bubble.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = wlp.x;
                        startY = wlp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved = true;
                        if (moved) {
                            wlp.x = startX + (int) dx;
                            wlp.y = startY + (int) dy;
                            try {
                                wm.updateViewLayout(root, wlp);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) setMonitor(live == null);
                        return true;
                    default:
                        return false;
                }
            }
        });

        applyVoiceUi();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        setMonitor(false);
        if (root != null && wm != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
        }
        inst = null;
        super.onDestroy();
    }

    public void setVoice(int mode) {
        voice = mode;
        prefs.edit().putInt("hud_voice", mode).apply();
        applyVoiceUi();
        if (live != null) live.setParams(currentParams());
    }

    private void applyVoiceUi() {
        bubble.setBackgroundResource(voice == VoicePreset.MODE_FEMALE
                ? R.drawable.bg_hud_female : R.drawable.bg_hud_male);
        maleBtn.setSelected(voice == VoicePreset.MODE_MALE);
        femaleBtn.setSelected(voice == VoicePreset.MODE_FEMALE);
        maleBtn.setTextColor(getResources().getColor(
                voice == VoicePreset.MODE_MALE ? R.color.window_bg : R.color.text_primary, null));
        femaleBtn.setTextColor(getResources().getColor(
                voice == VoicePreset.MODE_FEMALE ? R.color.window_bg : R.color.text_primary, null));
    }

    private VoiceEngine.Params currentParams() {
        float strength = prefs.getInt("hud_strength", 85) / 100f;
        return VoicePreset.params(voice, strength);
    }

    public void setMonitor(boolean on) {
        if (on && live == null) {
            live = new LiveChanger(new LiveChanger.LevelListener() {
                @Override
                public void onLevel(float rms) {
                }

                @Override
                public void onLatency(long millis) {
                }
            });
            live.setParams(currentParams());
            live.start();
            monitorBtn.setText(R.string.hud_monitor_on);
        } else if (!on && live != null) {
            live.stop();
            live = null;
            monitorBtn.setText(R.string.hud_monitor_off);
        }
    }

    public void broadcastLast() {
        List<ClipLibrary.Clip> clips = ClipLibrary.list(this);
        if (clips.isEmpty()) return;
        BroadcastPlayer.broadcast(this, clips.get(0), null);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    getString(R.string.hud_channel), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(R.drawable.ic_live)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.hud_notif))
                .setOngoing(true)
                .setContentIntent(pi);
        return b.build();
    }
}
