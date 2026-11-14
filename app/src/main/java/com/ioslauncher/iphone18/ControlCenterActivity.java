package com.ioslauncher.iphone18;

import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ioslauncher.iphone18.util.SystemToggles;
import com.ioslauncher.iphone18.util.Ui;

/** iOS Control Center. */
public class ControlCenterActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 2001;

    private ImageView ivAirplane;
    private ImageView ivWifi;
    private ImageView ivBt;
    private ImageView ivDnd;
    private ImageView ivRotation;
    private ImageView ivFlash;
    private ImageView ivCamera;
    private SeekBar seekBrightness;
    private SeekBar seekVolume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_control_center);

        GridLayout grid = findViewById(R.id.toggle_grid);
        ivAirplane = addToggle(grid, R.drawable.ic_cc_airplane, "Airplane");
        ivWifi = addToggle(grid, R.drawable.ic_cc_wifi, "Wi-Fi");
        ivBt = addToggle(grid, R.drawable.ic_cc_bt, "Bluetooth");
        ivDnd = addToggle(grid, R.drawable.ic_cc_moon, "Focus");

        ivRotation = findViewById(R.id.btn_cc_rotation);
        ivFlash = findViewById(R.id.btn_cc_flash);
        ivCamera = findViewById(R.id.btn_cc_camera);
        seekBrightness = findViewById(R.id.seek_brightness);
        seekVolume = findViewById(R.id.seek_volume);

        ivAirplane.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.vibrate(ControlCenterActivity.this);
                SystemToggles.openAirplaneSettings(ControlCenterActivity.this);
            }
        });
        ivWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.vibrate(ControlCenterActivity.this);
                SystemToggles.openWifiPanel(ControlCenterActivity.this);
            }
        });
        ivBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.vibrate(ControlCenterActivity.this);
                if (android.os.Build.VERSION.SDK_INT >= 31
                        && ContextCompat.checkSelfPermission(ControlCenterActivity.this,
                        android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ControlCenterActivity.this,
                            new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 2002);
                    return;
                }
                SystemToggles.openBtSettings(ControlCenterActivity.this);
            }
        });
        ivDnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.vibrate(ControlCenterActivity.this);
                SystemToggles.toggleDnd(ControlCenterActivity.this);
                refreshStates();
            }
        });
        ivRotation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.vibrate(ControlCenterActivity.this);
                SystemToggles.toggleRotation(ControlCenterActivity.this);
                refreshStates();
            }
        });
        ivFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.vibrate(ControlCenterActivity.this);
                if (ContextCompat.checkSelfPermission(ControlCenterActivity.this,
                        android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ControlCenterActivity.this,
                            new String[]{android.Manifest.permission.CAMERA}, REQ_CAMERA);
                    return;
                }
                if (!SystemToggles.toggleTorch(ControlCenterActivity.this)) {
                    Toast.makeText(ControlCenterActivity.this, "Flashlight unavailable", Toast.LENGTH_SHORT).show();
                }
                refreshStates();
            }
        });
        ivCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    android.content.Intent i = new android.content.Intent(
                            android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                    startActivity(i);
                    finish();
                } catch (Exception e) {
                    Toast.makeText(ControlCenterActivity.this, "No camera app", Toast.LENGTH_SHORT).show();
                }
            }
        });

        seekBrightness.setMax(255);
        seekBrightness.setProgress(SystemToggles.brightness(this));
        seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                try {
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = Math.max(0.02f, progress / 255f);
                    getWindow().setAttributes(lp);
                } catch (Exception ignored) {
                }
                SystemToggles.setBrightness(ControlCenterActivity.this, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        seekVolume.setMax(SystemToggles.volumeMax(this));
        seekVolume.setProgress(SystemToggles.volumeNow(this));
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) SystemToggles.setVolume(ControlCenterActivity.this, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // swipe up = band karo
        final float[] down = new float[2];
        findViewById(android.R.id.content).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    float dy = e.getRawY() - down[1];
                    if (dy < -Ui.dp(ControlCenterActivity.this, 120)) finish();
                }
                return false;
            }
        });

        refreshStates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStates();
        seekVolume.setMax(SystemToggles.volumeMax(this));
        seekVolume.setProgress(SystemToggles.volumeNow(this));
    }

    private ImageView addToggle(GridLayout grid, int iconRes, String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        cell.setLayoutParams(lp);

        ImageView iv = new ImageView(this);
        int s = Ui.dp(this, 52);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(s, s);
        iv.setLayoutParams(ilp);
        iv.setBackgroundResource(R.drawable.bg_cc_circle);
        iv.setImageResource(iconRes);
        iv.setPadding(Ui.dp(this, 13), Ui.dp(this, 13), Ui.dp(this, 13), Ui.dp(this, 13));
        cell.addView(iv);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(11);
        tv.setGravity(Gravity.CENTER);
        cell.addView(tv);

        grid.addView(cell);
        return iv;
    }

    private void refreshStates() {
        setToggle(ivAirplane, SystemToggles.isAirplaneOn(this), 0xFFFF9F0A);
        setToggle(ivWifi, SystemToggles.isWifiOn(this), 0xFF0A84FF);
        setToggle(ivBt, SystemToggles.isBtOn(this), 0xFF0A84FF);
        setToggle(ivDnd, SystemToggles.isDndOn(this), 0xFF5E5CE6);
        setToggle(ivRotation, SystemToggles.isRotationLocked(this), 0xFFFF453A);
        setToggle(ivFlash, SystemToggles.isTorchOn(), 0xFFFFFFFF);
    }

    private void setToggle(ImageView iv, boolean on, int onColor) {
        try {
            GradientDrawable bg = (GradientDrawable) iv.getBackground().mutate();
            bg.setColor(on ? onColor : 0xFF3A3A3C);
            iv.setBackground(bg);
            iv.setColorFilter(on && onColor == 0xFFFFFFFF ? 0xFF000000 : 0xFFFFFFFF);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            SystemToggles.toggleTorch(this);
            refreshStates();
        }
    }
}
