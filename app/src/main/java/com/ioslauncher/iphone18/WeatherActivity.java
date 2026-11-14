package com.ioslauncher.iphone18;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ioslauncher.iphone18.util.Ui;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** iOS Weather (offline demo, waqt ke hisaab se). */
public class WeatherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_weather);

        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);

        int temp = tempFor(hour);
        ((TextView) findViewById(R.id.w_temp)).setText(temp + "°");
        ((TextView) findViewById(R.id.w_desc)).setText(descFor(hour));
        ((TextView) findViewById(R.id.w_hilo)).setText("H:" + (temp + 2) + "°  L:" + (temp - 6) + "°");

        LinearLayout hourly = findViewById(R.id.w_hourly);
        hourly.removeAllViews();
        SimpleDateFormat f = new SimpleDateFormat("ha", Locale.getDefault());
        for (int i = 0; i < 6; i++) {
            Calendar hc = (Calendar) c.clone();
            hc.add(Calendar.HOUR_OF_DAY, i);
            int h = hc.get(Calendar.HOUR_OF_DAY);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            col.setLayoutParams(lp);
            TextView t1 = new TextView(this);
            t1.setText(i == 0 ? "Now" : f.format(hc.getTime()));
            t1.setTextColor(0xFFFFFFFF);
            t1.setTextSize(12);
            t1.setGravity(Gravity.CENTER);
            col.addView(t1);
            TextView t2 = new TextView(this);
            t2.setText(iconFor(h));
            t2.setTextSize(22);
            t2.setGravity(Gravity.CENTER);
            col.addView(t2);
            TextView t3 = new TextView(this);
            t3.setText(tempFor(h) + "°");
            t3.setTextColor(0xFFFFFFFF);
            t3.setTextSize(15);
            t3.setGravity(Gravity.CENTER);
            col.addView(t3);
            hourly.addView(col);
        }
    }

    private int tempFor(int hour) {
        if (hour < 5) return 23;
        if (hour < 8) return 25;
        if (hour < 12) return 29;
        if (hour < 16) return 32;
        if (hour < 19) return 30;
        if (hour < 22) return 27;
        return 24;
    }

    private String descFor(int hour) {
        if (hour < 5) return "Clear Night";
        if (hour < 8) return "Morning Sun";
        if (hour < 16) return "Mostly Sunny";
        if (hour < 19) return "Golden Evening";
        return "Clear Night";
    }

    private String iconFor(int hour) {
        if (hour >= 6 && hour < 18) return "☀";
        return "🌙";
    }
}
