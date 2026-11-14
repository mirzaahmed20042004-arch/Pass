package com.ioslauncher.iphone18;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ioslauncher.iphone18.service.NotifListener;
import com.ioslauncher.iphone18.util.IconHelper;
import com.ioslauncher.iphone18.util.Prefs;
import com.ioslauncher.iphone18.util.Ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** iOS top shutter (notification center). */
public class NotificationCenterActivity extends AppCompatActivity implements NotifListener.Listener {

    private TextView timeView;
    private TextView dateView;
    private TextView emptyView;
    private RecyclerView list;
    private final List<NotifListener.Item> items = new ArrayList<>();
    private NAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_notification_center);

        timeView = findViewById(R.id.nc_time);
        dateView = findViewById(R.id.nc_date);
        emptyView = findViewById(R.id.empty_text);
        list = findViewById(R.id.notif_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NAdapter();
        list.setAdapter(adapter);

        updateClock();
        handler.post(tick);

        findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NotifListener.clearAll();
                refresh();
            }
        });

        emptyView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!NotifListener.isEnabled(NotificationCenterActivity.this)) {
                    NotifListener.openSettings(NotificationCenterActivity.this);
                }
            }
        });

        final float[] down = new float[2];
        findViewById(android.R.id.content).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    float dy = e.getRawY() - down[1];
                    if (dy < -Ui.dp(NotificationCenterActivity.this, 120)) finish();
                }
                return false;
            }
        });

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
    public void onNotifsChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        });
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

    private void refresh() {
        items.clear();
        items.addAll(NotifListener.snapshot());
        adapter.notifyDataSetChanged();
        if (!NotifListener.isEnabled(this)) {
            emptyView.setText("Tap to enable notification access");
            emptyView.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
        } else if (items.isEmpty()) {
            emptyView.setText("No Notifications");
            emptyView.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
        }
    }

    private class NAdapter extends RecyclerView.Adapter<NAdapter.NVH> {
        @NonNull
        @Override
        public NVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(NotificationCenterActivity.this)
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
                Bitmap b = IconHelper.roundedAppIcon(NotificationCenterActivity.this, it.pkg,
                        Prefs.iconStyle(NotificationCenterActivity.this),
                        Ui.dp(NotificationCenterActivity.this, 80));
                h.icon.setImageBitmap(b);
            } catch (Exception ignored) {
            }
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (it.tap != null) {
                            it.tap.send();
                            finish();
                        }
                    } catch (Exception e) {
                        Toast.makeText(NotificationCenterActivity.this, "Can't open", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    NotifListener.clearOne(it.key);
                    refresh();
                    return true;
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
