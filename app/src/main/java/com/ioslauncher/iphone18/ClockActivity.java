package com.ioslauncher.iphone18;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ioslauncher.iphone18.util.Ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** iOS Clock + stopwatch. */
public class ClockActivity extends AppCompatActivity {

    private TextView timeView;
    private TextView dateView;
    private TextView swView;
    private Button swStart;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long swBase = 0;
    private long swPaused = 0;
    private boolean swRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_clock);

        timeView = findViewById(R.id.clock_time);
        dateView = findViewById(R.id.clock_date);
        swView = findViewById(R.id.stopwatch_text);
        swStart = findViewById(R.id.btn_sw_start);

        swStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (swRunning) {
                    swPaused = SystemClock.elapsedRealtime() - swBase;
                    swRunning = false;
                    swStart.setText("Start");
                } else {
                    swBase = SystemClock.elapsedRealtime() - swPaused;
                    swRunning = true;
                    swStart.setText("Stop");
                    handler.post(swTick);
                }
            }
        });
        findViewById(R.id.btn_sw_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swRunning = false;
                swPaused = 0;
                swStart.setText("Start");
                swView.setText("00:00.0");
            }
        });

        handler.post(clockTick);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(swTick);
        super.onDestroy();
    }

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            try {
                Date now = new Date();
                timeView.setText(new SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(now));
                dateView.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now));
            } catch (Exception ignored) {
            }
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable swTick = new Runnable() {
        @Override
        public void run() {
            if (!swRunning) return;
            long ms = SystemClock.elapsedRealtime() - swBase;
            long m = ms / 60000;
            long s = (ms / 1000) % 60;
            long d = (ms / 100) % 10;
            swView.setText(String.format(Locale.US, "%02d:%02d.%d", m, s, d));
            handler.postDelayed(this, 100);
        }
    };
}
