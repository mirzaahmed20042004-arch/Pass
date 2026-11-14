package com.ioslauncher.iphone18;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.ioslauncher.iphone18.receiver.AdminReceiver;
import com.ioslauncher.iphone18.service.LockService;
import com.ioslauncher.iphone18.service.NotifListener;
import com.ioslauncher.iphone18.service.OverlayService;
import com.ioslauncher.iphone18.service.VolumeKeyService;
import com.ioslauncher.iphone18.util.IconHelper;
import com.ioslauncher.iphone18.util.Prefs;
import com.ioslauncher.iphone18.util.Ui;

/** iOS style Settings. */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_HOME_ROLE = 4001;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        Ui.lightStatusBar(this, true);
        setContentView(R.layout.activity_settings);
        root = findViewById(R.id.settings_root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        root.removeAllViews();

        LinearLayout g1 = addSection("Personalize");
        addRow(g1, "Wallpaper", "50 iPhone wallpapers", true, new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SettingsActivity.this, WallpaperPickerActivity.class));
            }
        });
        addRow(g1, "App Icons", iconStyleName(), true, new Runnable() {
            @Override
            public void run() {
                pickIconStyle();
            }
        });
        addRow(g1, "Home Grid", Prefs.gridCols(this) + " x " + Prefs.gridRows(this), true, new Runnable() {
            @Override
            public void run() {
                pickGrid();
            }
        });
        addSwitch(g1, "Show App Names", Prefs.showLabels(this), new SwitchListener() {
            @Override
            public void onChange(boolean v) {
                Prefs.setShowLabels(SettingsActivity.this, v);
            }
        });
        addSwitch(g1, "Haptics", Prefs.haptics(this), new SwitchListener() {
            @Override
            public void onChange(boolean v) {
                Prefs.setHaptics(SettingsActivity.this, v);
            }
        });

        LinearLayout g2 = addSection("iOS System");
        addSwitch(g2, "Dynamic Island", Prefs.island(this), new SwitchListener() {
            @Override
            public void onChange(boolean v) {
                Prefs.setIsland(SettingsActivity.this, v);
                OverlayService.refreshViews();
            }
        });
        addSwitch(g2, "Control Center Gesture", Prefs.ccGesture(this), new SwitchListener() {
            @Override
            public void onChange(boolean v) {
                Prefs.setCcGesture(SettingsActivity.this, v);
                OverlayService.startIfNeeded(SettingsActivity.this);
            }
        });
        addSwitch(g2, "Notification Center Gesture", Prefs.ncGesture(this), new SwitchListener() {
            @Override
            public void onChange(boolean v) {
                Prefs.setNcGesture(SettingsActivity.this, v);
                OverlayService.startIfNeeded(SettingsActivity.this);
            }
        });
        addRow(g2, "iOS Volume Panel", volHudSub(), true, new Runnable() {
            @Override
            public void run() {
                if (!VolumeKeyService.isEnabled(SettingsActivity.this)) {
                    Toast.makeText(SettingsActivity.this,
                            "Enable iPhone Launcher in Accessibility", Toast.LENGTH_LONG).show();
                    VolumeKeyService.openSettings(SettingsActivity.this);
                } else {
                    Prefs.setVolHud(SettingsActivity.this, !Prefs.volHud(SettingsActivity.this));
                    OverlayService.startIfNeeded(SettingsActivity.this);
                    build();
                }
            }
        });
        addSwitch(g2, "AssistiveTouch", Prefs.assistive(this), new SwitchListener() {
            @Override
            public void onChange(boolean v) {
                Prefs.setAssistive(SettingsActivity.this, v);
                OverlayService.startIfNeeded(SettingsActivity.this);
            }
        });
        addSwitch(g2, "iOS Lock Screen", Prefs.lockScreen(this), new SwitchListener() {
            @Override
            public void onChange(boolean v) {
                Prefs.setLockScreen(SettingsActivity.this, v);
                LockService.startIfNeeded(SettingsActivity.this);
            }
        });
        addRow(g2, "Lock Screen Passcode",
                Prefs.passcode(this).isEmpty() ? "Swipe only" : "4-digit set", true, new Runnable() {
                    @Override
                    public void run() {
                        pickPasscode();
                    }
                });

        LinearLayout g3 = addSection("Default Setup");
        addRow(g3, "Set as Default Launcher", isDefaultLauncher() ? "Active ✓" : "Tap to set", true, new Runnable() {
            @Override
            public void run() {
                requestHomeRole();
            }
        });
        addRow(g3, "Notification Access",
                NotifListener.isEnabled(this) ? "Allowed ✓" : "Tap to allow", true, new Runnable() {
                    @Override
                    public void run() {
                        NotifListener.openSettings(SettingsActivity.this);
                    }
                });
        addRow(g3, "Draw Over Other Apps",
                android.provider.Settings.canDrawOverlays(this) ? "Allowed ✓" : "Tap to allow", true, new Runnable() {
                    @Override
                    public void run() {
                        requestOverlay();
                    }
                });
        addRow(g3, "Device Admin (Lock)", isAdmin() ? "Active ✓" : "Tap to enable", true, new Runnable() {
            @Override
            public void run() {
                requestAdmin();
            }
        });
        addRow(g3, "Hidden Apps", Prefs.hiddenApps(this).size() + " hidden", true, new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SettingsActivity.this, HiddenAppsActivity.class));
            }
        });

        LinearLayout g4 = addSection("About");
        addRow(g4, "Version", "1.0  •  Android 10 – 17+", false, null);
        addRow(g4, "Reset Launcher", "Home layout + settings", true, new Runnable() {
            @Override
            public void run() {
                confirmReset();
            }
        });
    }

    private interface SwitchListener {
        void onChange(boolean v);
    }

    private LinearLayout addSection(String title) {
        TextView tv = new TextView(this);
        tv.setText(title.toUpperCase());
        tv.setTextColor(0xFF6E6E73);
        tv.setTextSize(13);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(Ui.dp(this, 8), Ui.dp(this, 18), 0, Ui.dp(this, 6));
        tv.setLayoutParams(tlp);
        root.addView(tv);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_grouped);
        root.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void addRow(LinearLayout box, String title, String sub, boolean chevron, final Runnable action) {
        if (box.getChildCount() > 0) {
            View div = new View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            dlp.setMargins(Ui.dp(this, 16), 0, 0, 0);
            div.setLayoutParams(dlp);
            div.setBackgroundColor(0xFFD1D1D6);
            box.addView(div);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 13), Ui.dp(this, 16), Ui.dp(this, 13));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(tlp);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFF000000);
        t.setTextSize(16);
        texts.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextColor(0xFF6E6E73);
            s.setTextSize(13);
            texts.addView(s);
        }
        row.addView(texts);
        if (chevron) {
            TextView c = new TextView(this);
            c.setText("›");
            c.setTextColor(0xFFAAAAAA);
            c.setTextSize(22);
            row.addView(c);
        }
        if (action != null) {
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    action.run();
                }
            });
        }
        box.addView(row);
    }

    private void addSwitch(LinearLayout box, String title, boolean value, final SwitchListener l) {
        if (box.getChildCount() > 0) {
            View div = new View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            dlp.setMargins(Ui.dp(this, 16), 0, 0, 0);
            div.setLayoutParams(dlp);
            div.setBackgroundColor(0xFFD1D1D6);
            box.addView(div);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFF000000);
        t.setTextSize(16);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        t.setLayoutParams(tlp);
        row.addView(t);
        final SwitchCompat sw = new SwitchCompat(this);
        sw.setChecked(value);
        sw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                l.onChange(sw.isChecked());
            }
        });
        row.addView(sw);
        box.addView(row);
    }

    // ---------------- actions ----------------

    private String iconStyleName() {
        int s = Prefs.iconStyle(this);
        if (s == Prefs.ICON_DARK) return "Dark";
        if (s == Prefs.ICON_TINTED) return "Tinted";
        return "Light";
    }

    private String volHudSub() {
        if (!VolumeKeyService.isEnabled(this)) return "Needs Accessibility permission";
        return Prefs.volHud(this) ? "On ✓" : "Off";
    }

    private void pickIconStyle() {
        final String[] names = {"Light", "Dark", "Tinted"};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setTitle("App Icons")
                .setSingleChoiceItems(names, Prefs.iconStyle(this), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Prefs.setIconStyle(SettingsActivity.this, which);
                        IconHelper.clearCache();
                        d.dismiss();
                        build();
                    }
                })
                .show();
    }

    private void pickGrid() {
        final String[] names = {"4 x 5", "4 x 6", "5 x 5", "5 x 6"};
        final int[][] vals = {{4, 5}, {4, 6}, {5, 5}, {5, 6}};
        int cur = 0;
        for (int i = 0; i < vals.length; i++) {
            if (vals[i][0] == Prefs.gridCols(this) && vals[i][1] == Prefs.gridRows(this)) cur = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setTitle("Home Grid")
                .setSingleChoiceItems(names, cur, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Prefs.setGrid(SettingsActivity.this, vals[which][0], vals[which][1]);
                        d.dismiss();
                        build();
                    }
                })
                .show();
    }

    private void pickPasscode() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        input.setHint("4-digit (empty = swipe only)");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setTitle("Lock Screen Passcode")
                .setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Prefs.setPasscode(SettingsActivity.this, input.getText().toString().trim());
                        build();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean isDefaultLauncher() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            ComponentName cn = home.resolveActivity(getPackageManager());
            return cn != null && getPackageName().equals(cn.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private void requestHomeRole() {
        try {
            RoleManager rm = (RoleManager) getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                Toast.makeText(this, "Already default launcher", Toast.LENGTH_SHORT).show();
                return;
            }
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                Intent i = rm.createRequestRoleIntent(RoleManager.ROLE_HOME);
                startActivityForResult(i, REQ_HOME_ROLE);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(i, "Select Home app"));
        } catch (Exception ignored) {
        }
    }

    private void requestOverlay() {
        try {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Already allowed", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private boolean isAdmin() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            return dpm != null && dpm.isAdminActive(new ComponentName(this, AdminReceiver.class));
        } catch (Exception e) {
            return false;
        }
    }

    private void requestAdmin() {
        try {
            if (isAdmin()) {
                Toast.makeText(this, "Already enabled", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, AdminReceiver.class));
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "To lock your phone from the iOS lock screen.");
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setTitle("Reset Launcher?")
                .setMessage("Home layout, folders, hidden apps and settings will reset.")
                .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Prefs.resetAll(SettingsActivity.this);
                        IconHelper.clearCache();
                        OverlayService.startIfNeeded(SettingsActivity.this);
                        LockService.startIfNeeded(SettingsActivity.this);
                        build();
                        Toast.makeText(SettingsActivity.this, "Reset done", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_HOME_ROLE) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "iPhone Launcher is now default", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Not set as default", Toast.LENGTH_SHORT).show();
            }
            build();
        }
    }
}
