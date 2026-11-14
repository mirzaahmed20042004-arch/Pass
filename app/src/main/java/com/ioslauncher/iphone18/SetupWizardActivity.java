package com.ioslauncher.iphone18;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ioslauncher.iphone18.service.LockService;
import com.ioslauncher.iphone18.service.NotifListener;
import com.ioslauncher.iphone18.service.OverlayService;
import com.ioslauncher.iphone18.util.Prefs;
import com.ioslauncher.iphone18.util.Ui;

/** Pehli baar setup guide. */
public class SetupWizardActivity extends AppCompatActivity {

    private static final int REQ_HOME_ROLE = 5001;
    private LinearLayout stepsBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_setup);
        stepsBox = findViewById(R.id.steps_box);

        findViewById(R.id.btn_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setFirstRunDone(SetupWizardActivity.this);
                OverlayService.startIfNeeded(SetupWizardActivity.this);
                LockService.startIfNeeded(SetupWizardActivity.this);
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildSteps();
    }

    private void buildSteps() {
        stepsBox.removeAllViews();
        addStep("1. Default Launcher", "Set iPhone Launcher as Home so the iOS look stays.",
                isDefaultLauncher() ? "Done ✓" : "Allow", new Runnable() {
                    @Override
                    public void run() {
                        requestHomeRole();
                    }
                });
        addStep("2. Notifications", "Allow notification access for the iOS top shutter.",
                NotifListener.isEnabled(this) ? "Done ✓" : "Allow", new Runnable() {
                    @Override
                    public void run() {
                        NotifListener.openSettings(SetupWizardActivity.this);
                    }
                });
        addStep("3. Floating Features", "Allow display over other apps for volume panel, AssistiveTouch and gestures.",
                Settings.canDrawOverlays(this) ? "Done ✓" : "Allow", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (Exception ignored) {
                        }
                    }
                });
        addStep("4. Wallpaper", "Pick one of 50 iPhone wallpapers.", "Pick", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SetupWizardActivity.this, WallpaperPickerActivity.class));
            }
        });
    }

    private void addStep(String title, String desc, String btnText, final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int pad = Ui.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        card.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(17);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(0xB3FFFFFF);
        d.setTextSize(14);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.dp(this, 4);
        d.setLayoutParams(dlp);
        card.addView(d);

        Button b = new Button(this);
        b.setText(btnText);
        b.setAllCaps(false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 8);
        blp.gravity = Gravity.END;
        b.setLayoutParams(blp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        card.addView(b);
        stepsBox.addView(card);
    }

    private boolean isDefaultLauncher() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            android.content.ComponentName cn = home.resolveActivity(getPackageManager());
            return cn != null && getPackageName().equals(cn.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private void requestHomeRole() {
        try {
            RoleManager rm = (RoleManager) getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Toast.makeText(this, "Already default launcher", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME_ROLE);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            startActivity(Intent.createChooser(i, "Select Home app"));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_HOME_ROLE) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "iPhone Launcher is now default", Toast.LENGTH_SHORT).show();
            }
            buildSteps();
        }
    }
}
