package com.ioslauncher.iphone18;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.ioslauncher.iphone18.adapter.AppGridAdapter;
import com.ioslauncher.iphone18.adapter.HomePagesAdapter;
import com.ioslauncher.iphone18.model.AppInfo;
import com.ioslauncher.iphone18.model.HomeItem;
import com.ioslauncher.iphone18.service.OverlayService;
import com.ioslauncher.iphone18.util.AppLoader;
import com.ioslauncher.iphone18.util.IconHelper;
import com.ioslauncher.iphone18.util.Prefs;
import com.ioslauncher.iphone18.util.Ui;
import com.ioslauncher.iphone18.util.WallpaperHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** iPhone jaisi home screen. */
public class LauncherActivity extends AppCompatActivity {

    private RecyclerView pages;
    private LinearLayout dots;
    private LinearLayout dock;
    private ImageView wallpaperView;
    private TextView sbTime;
    private View sbIsland;
    private View sbBatteryFill;
    private TextView widgetTime;
    private TextView widgetDate;
    private TextView widgetBattery;

    private List<AppInfo> allApps = new ArrayList<>();
    private final Map<String, AppInfo> byPkg = new HashMap<>();
    private HomePagesAdapter pagerAdapter;
    private boolean editMode = false;
    private String buildSignature = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static Bitmap wallpaperCache;
    private static String wallpaperCacheKey = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        Ui.hideSystemBars(this);
        setContentView(R.layout.activity_launcher);

        if (Prefs.isFirstRun(this)) {
            startActivity(new Intent(this, SetupWizardActivity.class));
        }

        pages = findViewById(R.id.pages);
        dots = findViewById(R.id.dots);
        dock = findViewById(R.id.dock);
        wallpaperView = findViewById(R.id.wallpaper);
        sbTime = findViewById(R.id.sb_time);
        sbIsland = findViewById(R.id.sb_island);
        sbBatteryFill = findViewById(R.id.sb_battery_fill);
        widgetTime = findViewById(R.id.widget_time);
        widgetDate = findViewById(R.id.widget_date);
        widgetBattery = findViewById(R.id.widget_battery);

        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        pages.setLayoutManager(lm);
        new PagerSnapHelper().attachToRecyclerView(pages);
        pages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                updateDots();
            }
        });

        findViewById(R.id.fake_search).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSearch();
            }
        });

        setupGestures();
        setupStatusZones();
        loadWallpaper();
        updateClock();
        handler.post(clockTick);

        OverlayService.startIfNeeded(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.hideSystemBars(this);
        if (sbIsland != null) sbIsland.setVisibility(Prefs.island(this) ? View.VISIBLE : View.GONE);
        String sig = Prefs.iconStyle(this) + "|" + Prefs.gridCols(this) + "|" + Prefs.gridRows(this)
                + "|" + Prefs.showLabels(this) + "|" + Prefs.hiddenApps(this).size()
                + "|" + Prefs.folders(this).hashCode();
        if (!sig.equals(buildSignature)) {
            reloadApps();
        } else {
            updateClock();
        }
        loadWallpaper();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.hideSystemBars(this);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (editMode) {
            setEditMode(false);
            return;
        }
        // Home launcher: back par kuch nahi
    }

    // ---------------- apps ----------------

    private void reloadApps() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> loaded = AppLoader.load(LauncherActivity.this, true);
                final List<HomeItem> items = buildItems(loaded);
                final int cols = Prefs.gridCols(LauncherActivity.this);
                final int rows = Prefs.gridRows(LauncherActivity.this);
                buildSignature = Prefs.iconStyle(LauncherActivity.this) + "|" + cols + "|" + rows
                        + "|" + Prefs.showLabels(LauncherActivity.this) + "|"
                        + Prefs.hiddenApps(LauncherActivity.this).size()
                        + "|" + Prefs.folders(LauncherActivity.this).hashCode();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        allApps = loaded;
                        byPkg.clear();
                        for (AppInfo a : loaded) byPkg.put(a.packageName, a);
                        pagerAdapter = new HomePagesAdapter(LauncherActivity.this, items, cols, rows, byPkg, gridListener);
                        pages.setAdapter(pagerAdapter);
                        buildDots(pagerAdapter.pageCount());
                        buildDock();
                        if (editMode) pagerAdapter.setEditMode(true);
                    }
                });
            }
        }).start();
    }

    private List<HomeItem> buildItems(List<AppInfo> loaded) {
        List<HomeItem> items = new ArrayList<>();
        items.add(HomeItem.internal("settings", "Settings"));
        items.add(HomeItem.internal("calculator", "Calculator"));
        items.add(HomeItem.internal("notes", "Notes"));
        items.add(HomeItem.internal("clock", "Clock"));
        items.add(HomeItem.internal("weather", "Weather"));
        items.add(HomeItem.internal("wallpapers", "Wallpapers"));

        Set<String> hidden = Prefs.hiddenApps(this);
        Set<String> inFolders = new HashSet<>();
        for (String f : Prefs.folders(this)) {
            int bar = f.indexOf('|');
            if (bar < 0) continue;
            String name = f.substring(0, bar);
            List<String> pkgs = new ArrayList<>();
            String rest = f.substring(bar + 1);
            if (!rest.isEmpty()) {
                for (String p : rest.split(",")) {
                    if (!p.isEmpty()) {
                        pkgs.add(p);
                        inFolders.add(p);
                    }
                }
            }
            if (!pkgs.isEmpty()) items.add(HomeItem.folder(name, pkgs));
        }
        for (AppInfo a : loaded) {
            if (hidden.contains(a.packageName)) continue;
            if (inFolders.contains(a.packageName)) continue;
            items.add(HomeItem.app(a));
        }
        return items;
    }

    private final AppGridAdapter.Listener gridListener = new AppGridAdapter.Listener() {
        @Override
        public void onTap(HomeItem item) {
            if (editMode) {
                showItemOptions(item);
                return;
            }
            openItem(item);
        }

        @Override
        public void onLong(HomeItem item) {
            Ui.vibrate(LauncherActivity.this);
            setEditMode(true);
        }

        @Override
        public void onBadge(HomeItem item) {
            if (item.kind == HomeItem.APP && item.app != null) {
                Prefs.setHidden(LauncherActivity.this, item.app.packageName, true);
                Toast.makeText(LauncherActivity.this, "Hidden — see Settings > Hidden Apps", Toast.LENGTH_SHORT).show();
                setEditMode(false);
                reloadApps();
            } else if (item.kind == HomeItem.FOLDER) {
                removeFolder(item);
            } else {
                Toast.makeText(LauncherActivity.this, "Built-in app can't be removed", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void setEditMode(boolean edit) {
        editMode = edit;
        if (pagerAdapter != null) pagerAdapter.setEditMode(edit);
    }

    private void openItem(HomeItem item) {
        try {
            if (item.kind == HomeItem.INTERNAL) {
                openInternal(item.internalKey);
            } else if (item.kind == HomeItem.FOLDER) {
                openFolder(item);
            } else if (item.kind == HomeItem.APP && item.app != null) {
                launchApp(item.app);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Can't open app", Toast.LENGTH_SHORT).show();
        }
    }

    private void openInternal(String key) {
        if (key == null) return;
        Class<?> cls = null;
        if (key.equals("settings")) cls = SettingsActivity.class;
        else if (key.equals("calculator")) cls = CalculatorActivity.class;
        else if (key.equals("notes")) cls = NotesActivity.class;
        else if (key.equals("clock")) cls = ClockActivity.class;
        else if (key.equals("weather")) cls = WeatherActivity.class;
        else if (key.equals("wallpapers")) cls = WallpaperPickerActivity.class;
        if (cls != null) startActivity(new Intent(this, cls));
    }

    public static void launchAppStatic(Context ctx, AppInfo app) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setClassName(app.packageName, app.className);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    private void launchApp(AppInfo app) {
        try {
            launchAppStatic(this, app);
        } catch (Exception e) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(app.packageName);
                if (i != null) startActivity(i);
                else Toast.makeText(this, "Can't open app", Toast.LENGTH_SHORT).show();
            } catch (Exception e2) {
                Toast.makeText(this, "Can't open app", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---------------- folders ----------------

    private void openFolder(final HomeItem folder) {
        try {
            View v = getLayoutInflater().inflate(R.layout.dialog_folder, null);
            final EditText nameBox = v.findViewById(R.id.folder_name);
            nameBox.setText(folder.folderName);
            RecyclerView grid = v.findViewById(R.id.folder_grid);
            grid.setLayoutManager(new GridLayoutManager(this, 4));
            final List<HomeItem> members = new ArrayList<>();
            for (String pkg : folder.folderPkgs) {
                AppInfo a = byPkg.get(pkg);
                if (a != null) members.add(HomeItem.app(a));
            }
            AppGridAdapter adapter = new AppGridAdapter(this, members, byPkg, new AppGridAdapter.Listener() {
                @Override
                public void onTap(HomeItem item) {
                    if (item.kind == HomeItem.APP) launchApp(item.app);
                }

                @Override
                public void onLong(HomeItem item) {
                    // folder se nikalo
                    if (item.kind == HomeItem.APP && item.app != null) {
                        removeFromFolder(folder, item.app.packageName);
                    }
                }

                @Override
                public void onBadge(HomeItem item) {
                }
            });
            grid.setAdapter(adapter);
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                    .setView(v)
                    .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            renameFolder(folder, nameBox.getText().toString().trim());
                        }
                    })
                    .setNegativeButton("Close", null)
                    .show();
        } catch (Exception ignored) {
        }
    }

    private void renameFolder(HomeItem folder, String newName) {
        if (newName.isEmpty()) return;
        Set<String> folders = Prefs.folders(this);
        Set<String> out = new HashSet<>();
        for (String f : folders) {
            int bar = f.indexOf('|');
            if (bar > 0 && f.substring(0, bar).equals(folder.folderName)) {
                out.add(newName + f.substring(bar));
            } else {
                out.add(f);
            }
        }
        Prefs.setFolders(this, out);
        reloadApps();
    }

    private void removeFolder(HomeItem folder) {
        Set<String> folders = Prefs.folders(this);
        Set<String> out = new HashSet<>();
        for (String f : folders) {
            int bar = f.indexOf('|');
            if (!(bar > 0 && f.substring(0, bar).equals(folder.folderName))) out.add(f);
        }
        Prefs.setFolders(this, out);
        setEditMode(false);
        reloadApps();
        Toast.makeText(this, "Folder removed, apps are back on Home", Toast.LENGTH_SHORT).show();
    }

    private void removeFromFolder(HomeItem folder, String pkg) {
        Set<String> folders = Prefs.folders(this);
        Set<String> out = new HashSet<>();
        for (String f : folders) {
            int bar = f.indexOf('|');
            if (bar > 0 && f.substring(0, bar).equals(folder.folderName)) {
                List<String> keep = new ArrayList<>();
                for (String p : f.substring(bar + 1).split(",")) {
                    if (!p.isEmpty() && !p.equals(pkg)) keep.add(p);
                }
                if (!keep.isEmpty()) {
                    StringBuilder sb = new StringBuilder(folder.folderName).append('|');
                    for (int i = 0; i < keep.size(); i++) {
                        if (i > 0) sb.append(',');
                        sb.append(keep.get(i));
                    }
                    out.add(sb.toString());
                }
            } else {
                out.add(f);
            }
        }
        Prefs.setFolders(this, out);
        reloadApps();
        Toast.makeText(this, "Removed from folder", Toast.LENGTH_SHORT).show();
    }

    // ---------------- item options ----------------

    private void showItemOptions(final HomeItem item) {
        if (item.kind == HomeItem.INTERNAL) {
            Toast.makeText(this, item.internalLabel, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> opts = new ArrayList<>();
        opts.add("Open");
        if (item.kind == HomeItem.APP) {
            opts.add("Move to Folder");
            opts.add("Hide App");
            opts.add("App Info");
            opts.add("Uninstall");
        } else if (item.kind == HomeItem.FOLDER) {
            opts.add("Rename");
            opts.add("Remove Folder");
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setTitle(item.label())
                .setItems(opts.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            setEditMode(false);
                            openItem(item);
                        } else if (item.kind == HomeItem.APP) {
                            if (which == 1) showMoveToFolder(item.app);
                            else if (which == 2) gridListener.onBadge(item);
                            else if (which == 3) openAppInfo(item.app.packageName);
                            else if (which == 4) uninstallApp(item.app.packageName);
                        } else if (item.kind == HomeItem.FOLDER) {
                            if (which == 1) openFolder(item);
                            else if (which == 2) removeFolder(item);
                        }
                    }
                })
                .show();
    }

    private void showMoveToFolder(final AppInfo app) {
        final Set<String> folders = Prefs.folders(this);
        final List<String> names = new ArrayList<>();
        for (String f : folders) {
            int bar = f.indexOf('|');
            if (bar > 0) names.add(f.substring(0, bar));
        }
        names.add("+ New Folder");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setTitle("Move to Folder")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == names.size() - 1) {
                            promptNewFolder(app);
                        } else {
                            addToFolder(names.get(which), app.packageName);
                        }
                    }
                })
                .show();
    }

    private void promptNewFolder(final AppInfo app) {
        final EditText input = new EditText(this);
        input.setHint("Folder name");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setTitle("New Folder")
                .setView(input)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) name = "Folder";
                        addToFolder(name, app.packageName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addToFolder(String name, String pkg) {
        Set<String> folders = Prefs.folders(this);
        Set<String> out = new HashSet<>();
        boolean found = false;
        for (String f : folders) {
            int bar = f.indexOf('|');
            if (bar > 0 && f.substring(0, bar).equals(name)) {
                found = true;
                String rest = f.substring(bar + 1);
                if (!rest.contains(pkg)) rest = rest.isEmpty() ? pkg : rest + "," + pkg;
                out.add(name + "|" + rest);
            } else {
                out.add(f);
            }
        }
        if (!found) out.add(name + "|" + pkg);
        Prefs.setFolders(this, out);
        setEditMode(false);
        reloadApps();
    }

    private void openAppInfo(String pkg) {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + pkg));
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void uninstallApp(String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_DELETE);
            i.setData(Uri.parse("package:" + pkg));
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    // ---------------- dots / dock ----------------

    private void buildDots(int count) {
        dots.removeAllViews();
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            int s = Ui.dp(this, i == 0 ? 8 : 7);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_home_indicator);
            dot.setAlpha(i == 0 ? 1f : 0.4f);
            dots.addView(dot);
        }
    }

    private void updateDots() {
        try {
            LinearLayoutManager lm = (LinearLayoutManager) pages.getLayoutManager();
            if (lm == null) return;
            int pos = lm.findFirstVisibleItemPosition();
            for (int i = 0; i < dots.getChildCount(); i++) {
                dots.getChildAt(i).setAlpha(i == pos ? 1f : 0.4f);
            }
        } catch (Exception ignored) {
        }
    }

    private void buildDock() {
        dock.removeAllViews();
        addDockItem("Phone", "phone", new Intent(Intent.ACTION_DIAL));
        Intent sms = new Intent(Intent.ACTION_VIEW);
        sms.setData(Uri.parse("smsto:"));
        addDockItem("Messages", "messages", sms);
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
        addDockItem("Safari", "safari", web);
        Intent cam = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        AppInfo camApp = findByKeyword("camera");
        final Intent camIntent = camApp != null ? intentFor(camApp) : cam;
        addDockItem("Camera", "camera", camIntent);
    }

    private void addDockItem(String label, String iconKey, final Intent intent) {
        View v = getLayoutInflater().inflate(R.layout.item_app, dock, false);
        ImageView icon = v.findViewById(R.id.app_icon);
        TextView tv = v.findViewById(R.id.app_label);
        tv.setText(label);
        tv.setVisibility(Prefs.showLabels(this) ? View.VISIBLE : View.GONE);
        int res = IconHelper.internalIcon(
                iconKey.equals("phone") ? "settings" : "generic");
        if (iconKey.equals("phone")) res = R.drawable.ic_ios_phone;
        else if (iconKey.equals("messages")) res = R.drawable.ic_ios_messages;
        else if (iconKey.equals("safari")) res = R.drawable.ic_ios_safari;
        else if (iconKey.equals("camera")) res = R.drawable.ic_ios_camera;
        try {
            android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(this, res);
            icon.setImageDrawable(d);
        } catch (Exception ignored) {
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        v.setLayoutParams(lp);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(LauncherActivity.this, "No app found", Toast.LENGTH_SHORT).show();
                }
            }
        });
        dock.addView(v);
    }

    private AppInfo findByKeyword(String kw) {
        for (AppInfo a : allApps) {
            if (a.packageName.toLowerCase(Locale.US).contains(kw)) return a;
        }
        return null;
    }

    private Intent intentFor(AppInfo app) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setClassName(app.packageName, app.className);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    // ---------------- gestures ----------------

    private void openSearch() {
        try {
            startActivity(new Intent(this, SearchActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception ignored) {
        }
    }

    private void setupGestures() {
        final View root = findViewById(R.id.root);
        final float[] down = new float[2];
        final long[] downT = new long[1];
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    downT[0] = System.currentTimeMillis();
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    float dy = e.getRawY() - down[1];
                    float dx = e.getRawX() - down[0];
                    long dt = System.currentTimeMillis() - downT[0];
                    if (!editMode && dy > Ui.dp(LauncherActivity.this, 120)
                            && Math.abs(dx) < Ui.dp(LauncherActivity.this, 160) && dt < 800) {
                        openSearch();
                    }
                }
                return false;
            }
        });
        root.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!editMode) startActivity(new Intent(LauncherActivity.this, WallpaperPickerActivity.class));
                return true;
            }
        });
        // khaali jagah tap = edit mode band
        pages.setOnTouchListener(new View.OnTouchListener() {
            final float[] d = new float[2];

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    d[0] = e.getRawX();
                    d[1] = e.getRawY();
                } else if (e.getAction() == MotionEvent.ACTION_UP && editMode) {
                    float dx = e.getRawX() - d[0];
                    float dy = e.getRawY() - d[1];
                    if (Math.hypot(dx, dy) < Ui.dp(LauncherActivity.this, 12)) setEditMode(false);
                }
                return false;
            }
        });
    }

    private void setupStatusZones() {
        try {
            final View statusBar = (View) findViewById(R.id.sb_time).getParent();
            final float[] down = new float[2];
            statusBar.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        down[0] = e.getRawX();
                        down[1] = e.getRawY();
                        return true;
                    }
                    if (e.getAction() == MotionEvent.ACTION_UP) {
                        float dy = e.getRawY() - down[1];
                        if (dy > Ui.dp(LauncherActivity.this, 40)) {
                            int w = getResources().getDisplayMetrics().widthPixels;
                            boolean right = down[0] > w * 0.55f;
                            startActivity(new Intent(LauncherActivity.this,
                                    right ? ControlCenterActivity.class : NotificationCenterActivity.class));
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        }
                        return true;
                    }
                    return true;
                }
            });
        } catch (Exception ignored) {
        }
    }

    // ---------------- clock / wallpaper ----------------

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 5000);
        }
    };

    private void updateClock() {
        try {
            Date now = new Date();
            String t = new SimpleDateFormat("h:mm", Locale.getDefault()).format(now);
            String d = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now);
            if (sbTime != null) sbTime.setText(t);
            if (widgetTime != null) widgetTime.setText(t);
            if (widgetDate != null) widgetDate.setText(d);
            updateBattery();
        } catch (Exception ignored) {
        }
    }

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryFrom(intent);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        try {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onStop() {
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {
        }
        super.onStop();
    }

    private void updateBattery() {
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            updateBatteryFrom(i);
        } catch (Exception ignored) {
        }
    }

    private void updateBatteryFrom(Intent i) {
        try {
            if (i == null) return;
            int level = i.getIntExtra("level", -1);
            int scale = i.getIntExtra("scale", 100);
            if (level < 0) return;
            int pct = Math.round(level * 100f / Math.max(1, scale));
            if (widgetBattery != null) widgetBattery.setText(pct + "%");
            if (sbBatteryFill != null) {
                int full = Ui.dp(this, 20);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sbBatteryFill.getLayoutParams();
                lp.width = Math.max(Ui.dp(this, 2), (int) (full * pct / 100f));
                sbBatteryFill.setLayoutParams(lp);
            }
        } catch (Exception ignored) {
        }
    }

    private void loadWallpaper() {
        final String asset = Prefs.wallpaperAsset(this);
        if (asset.equals(wallpaperCacheKey) && wallpaperCache != null && !wallpaperCache.isRecycled()) {
            wallpaperView.setImageBitmap(wallpaperCache);
            return;
        }
        final int sw = getResources().getDisplayMetrics().widthPixels;
        final int sh = getResources().getDisplayMetrics().heightPixels;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = WallpaperHelper.loadSampled(LauncherActivity.this, asset, sw / 2, sh / 2);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (b != null) {
                            wallpaperCache = b;
                            wallpaperCacheKey = asset;
                            wallpaperView.setImageBitmap(b);
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 9001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            OverlayService.startIfNeeded(this);
        }
    }
}
