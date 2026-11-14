package com.ioslauncher.iphone18;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ioslauncher.iphone18.service.NotifListener;
import com.ioslauncher.iphone18.util.IconHelper;
import com.ioslauncher.iphone18.util.Prefs;
import com.ioslauncher.iphone18.util.SystemToggles;
import com.ioslauncher.iphone18.util.Ui;
import com.ioslauncher.iphone18.util.WallpaperHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** iOS lock screen. */
public class LockScreenActivity extends AppCompatActivity implements NotifListener.Listener {

    private TextView timeView;
    private TextView dateView;
    private LinearLayout passcodeBox;
    private LinearLayout dotsBox;
    private final StringBuilder entered = new StringBuilder();
    private final List<NotifListener.Item> items = new ArrayList<>();
    private NAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        Ui.hideSystemBars(this);
        try {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } catch (Exception ignored) {
        }
        setContentView(R.layout.activity_lock);

        timeView = findViewById(R.id.lock_time);
        dateView = findViewById(R.id.lock_date);
        passcodeBox = findViewById(R.id.passcode_box);
        dotsBox = findViewById(R.id.passcode_dots);

        RecyclerView list = findViewById(R.id.lock_notifs);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NAdapter();
        list.setAdapter(adapter);

        updateClock();
        handler.post(tick);
        loadWallpaper();
        buildPad();
        refreshDots();

        findViewById(R.id.btn_lock_flash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(LockScreenActivity.this,
                        android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(LockScreenActivity.this,
                            new String[]{android.Manifest.permission.CAMERA}, 3001);
                    return;
                }
                SystemToggles.toggleTorch(LockScreenActivity.this);
            }
        });
        findViewById(R.id.btn_lock_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new android.content.Intent(
                            android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                } catch (Exception ignored) {
                }
            }
        });

        final float[] down = new float[2];
        findViewById(R.id.lock_root).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    float dy = e.getRawY() - down[1];
                    if (dy < -Ui.dp(LockScreenActivity.this, 110)) tryUnlock();
                }
                return false;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.hideSystemBars(this);
        NotifListener.addListener(this);
        refresh();
    }

    @Override
    protected void onPause() {
        NotifListener.removeListener(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // lock screen par back band
    }

    @Override
    public void onNotifsChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        });
    }

    private void tryUnlock() {
        Ui.vibrate(this);
        String code = Prefs.passcode(this);
        if (code == null || code.isEmpty()) {
            finish();
        } else {
            passcodeBox.setVisibility(View.VISIBLE);
            findViewById(R.id.lock_hint).setVisibility(View.GONE);
        }
    }

    private void buildPad() {
        GridLayout pad = findViewById(R.id.passcode_pad);
        pad.removeAllViews();
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"};
        for (final String k : keys) {
            TextView tv = new TextView(this);
            int s = Ui.dp(this, 72);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = s;
            lp.height = s;
            lp.setMargins(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setText(k);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(26);
            if (!k.isEmpty()) tv.setBackgroundResource(R.drawable.bg_lock_circle);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (k.isEmpty()) return;
                    Ui.vibrate(LockScreenActivity.this);
                    if (k.equals("⌫")) {
                        if (entered.length() > 0) entered.deleteCharAt(entered.length() - 1);
                    } else if (entered.length() < 4) {
                        entered.append(k);
                    }
                    refreshDots();
                    if (entered.length() == 4) {
                        if (entered.toString().equals(Prefs.passcode(LockScreenActivity.this))) {
                            finish();
                        } else {
                            Toast.makeText(LockScreenActivity.this, "Wrong passcode", Toast.LENGTH_SHORT).show();
                            entered.setLength(0);
                            refreshDots();
                        }
                    }
                }
            });
            pad.addView(tv);
        }
    }

    private void refreshDots() {
        dotsBox.removeAllViews();
        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            int s = Ui.dp(this, 14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.setMargins(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_cc_circle);
            try {
                android.graphics.drawable.GradientDrawable bg =
                        (android.graphics.drawable.GradientDrawable) dot.getBackground().mutate();
                bg.setColor(i < entered.length() ? 0xFFFFFFFF : 0x00000000);
                bg.setStroke(Ui.dp(this, 1), 0xFFFFFFFF);
                dot.setBackground(bg);
            } catch (Exception ignored) {
            }
            dotsBox.addView(dot);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 5000);
        }
    };

    private void updateClock() {
        try {
            Date now = new Date();
            timeView.setText(new SimpleDateFormat("h:mm", Locale.getDefault()).format(now));
            dateView.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now));
        } catch (Exception ignored) {
        }
    }

    private void loadWallpaper() {
        final String asset = Prefs.wallpaperAsset(this);
        final int sw = getResources().getDisplayMetrics().widthPixels;
        final int sh = getResources().getDisplayMetrics().heightPixels;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = WallpaperHelper.loadSampled(LockScreenActivity.this, asset, sw / 2, sh / 2);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (b != null) ((ImageView) findViewById(R.id.lock_wallpaper)).setImageBitmap(b);
                    }
                });
            }
        }).start();
    }

    private void refresh() {
        items.clear();
        items.addAll(NotifListener.snapshot());
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 3001 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            SystemToggles.toggleTorch(this);
        }
    }

    private class NAdapter extends RecyclerView.Adapter<NAdapter.NVH> {
        @NonNull
        @Override
        public NVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(LockScreenActivity.this)
                    .inflate(R.layout.item_notification, parent, false);
            return new NVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull NVH h, int position) {
            final NotifListener.Item it = items.get(position);
            h.app.setText(it.appLabel);
            h.time.setText(Ui.timeAgo(it.time));
            h.title.setText(it.title);
            h.text.setText(it.text);
            try {
                Bitmap b = IconHelper.roundedAppIcon(LockScreenActivity.this, it.pkg,
                        Prefs.iconStyle(LockScreenActivity.this), Ui.dp(LockScreenActivity.this, 80));
                h.icon.setImageBitmap(b);
            } catch (Exception ignored) {
            }
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tryUnlock();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class NVH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView app;
            TextView time;
            TextView title;
            TextView text;

            NVH(View v) {
                super(v);
                icon = v.findViewById(R.id.notif_icon);
                app = v.findViewById(R.id.notif_app);
                time = v.findViewById(R.id.notif_time);
                title = v.findViewById(R.id.notif_title);
                text = v.findViewById(R.id.notif_text);
            }
        }
    }
}
