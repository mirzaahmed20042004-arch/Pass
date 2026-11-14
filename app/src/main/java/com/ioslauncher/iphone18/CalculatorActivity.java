package com.ioslauncher.iphone18;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ioslauncher.iphone18.util.Ui;

import java.text.DecimalFormat;

/** iOS Calculator. */
public class CalculatorActivity extends AppCompatActivity {

    private TextView display;
    private String current = "0";
    private double stored = 0;
    private String op = null;
    private boolean fresh = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_calculator);
        display = findViewById(R.id.calc_display);
        buildPad();
        render();
    }

    private void buildPad() {
        GridLayout pad = findViewById(R.id.calc_pad);
        String[][] keys = {
                {"AC", "+/-", "%", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"0", ".", "="}
        };
        for (int r = 0; r < keys.length; r++) {
            for (int c = 0; c < keys[r].length; c++) {
                final String k = keys[r][c];
                TextView tv = new TextView(this);
                int s = Ui.dp(this, 76);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                if (k.equals("0")) {
                    lp.width = s * 2 + Ui.dp(this, 24);
                    lp.height = s;
                    lp.columnSpec = GridLayout.spec(c, 2);
                } else {
                    lp.width = s;
                    lp.height = s;
                }
                lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
                tv.setLayoutParams(lp);
                tv.setGravity(Gravity.CENTER);
                tv.setText(k);
                tv.setTextSize(28);
                if (c == 3 || (r == 4 && k.equals("="))) {
                    tv.setBackgroundResource(R.drawable.bg_calc_orange);
                    tv.setTextColor(0xFFFFFFFF);
                } else if (r == 0) {
                    tv.setBackgroundResource(R.drawable.bg_calc_light);
                    tv.setTextColor(0xFF000000);
                } else {
                    tv.setBackgroundResource(R.drawable.bg_calc_gray);
                    tv.setTextColor(0xFFFFFFFF);
                }
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        press(k);
                    }
                });
                pad.addView(tv);
            }
        }
    }

    private void press(String k) {
        Ui.vibrate(this);
        try {
            if (k.equals("AC")) {
                current = "0";
                stored = 0;
                op = null;
                fresh = true;
            } else if (k.equals("+/-")) {
                if (!current.equals("0")) {
                    current = current.startsWith("-") ? current.substring(1) : "-" + current;
                }
            } else if (k.equals("%")) {
                double v = Double.parseDouble(current) / 100.0;
                current = fmt(v);
                fresh = true;
            } else if (k.equals("÷") || k.equals("×") || k.equals("−") || k.equals("+")) {
                if (op != null && !fresh) equals();
                stored = Double.parseDouble(current);
                op = k;
                fresh = true;
            } else if (k.equals("=")) {
                equals();
            } else if (k.equals(".")) {
                if (fresh) {
                    current = "0.";
                    fresh = false;
                } else if (!current.contains(".")) {
                    current += ".";
                }
            } else {
                if (fresh) {
                    current = k;
                    fresh = false;
                } else if (current.replace("-", "").replace(".", "").length() < 9) {
                    current = current.equals("0") ? k : current + k;
                }
            }
        } catch (Exception e) {
            current = "Error";
            fresh = true;
        }
        render();
    }

    private void equals() {
        if (op == null) return;
        try {
            double b = Double.parseDouble(current);
            double r = 0;
            if (op.equals("+")) r = stored + b;
            else if (op.equals("−")) r = stored - b;
            else if (op.equals("×")) r = stored * b;
            else if (op.equals("÷")) r = b == 0 ? Double.NaN : stored / b;
            current = fmt(r);
            op = null;
            fresh = true;
        } catch (Exception e) {
            current = "Error";
            fresh = true;
        }
    }

    private String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "Error";
        DecimalFormat f = new DecimalFormat("0.########");
        return f.format(v);
    }

    private void render() {
        display.setText(current);
        display.setTextSize(current.length() > 7 ? 48 : 72);
    }
}
