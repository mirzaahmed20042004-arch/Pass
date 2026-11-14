package com.ioslauncher.iphone18.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Saari launcher settings ek jagah. */
public final class Prefs {
    private static final String FILE = "ios_launcher_prefs";

    public static final int ICON_LIGHT = 0;
    public static final int ICON_DARK = 1;
    public static final int ICON_TINTED = 2;

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isFirstRun(Context c) {
        return sp(c).getBoolean("first_run", true);
    }

    public static void setFirstRunDone(Context c) {
        sp(c).edit().putBoolean("first_run", false).apply();
    }

    public static int iconStyle(Context c) {
        return sp(c).getInt("icon_style", ICON_LIGHT);
    }

    public static void setIconStyle(Context c, int style) {
        sp(c).edit().putInt("icon_style", style).apply();
    }

    public static int gridCols(Context c) {
        return sp(c).getInt("grid_cols", 4);
    }

    public static int gridRows(Context c) {
        return sp(c).getInt("grid_rows", 5);
    }

    public static void setGrid(Context c, int cols, int rows) {
        sp(c).edit().putInt("grid_cols", cols).putInt("grid_rows", rows).apply();
    }

    public static boolean showLabels(Context c) {
        return sp(c).getBoolean("show_labels", true);
    }

    public static void setShowLabels(Context c, boolean v) {
        sp(c).edit().putBoolean("show_labels", v).apply();
    }

    public static boolean haptics(Context c) {
        return sp(c).getBoolean("haptics", true);
    }

    public static void setHaptics(Context c, boolean v) {
        sp(c).edit().putBoolean("haptics", v).apply();
    }

    public static boolean island(Context c) {
        return sp(c).getBoolean("island", true);
    }

    public static void setIsland(Context c, boolean v) {
        sp(c).edit().putBoolean("island", v).apply();
    }

    public static boolean ccGesture(Context c) {
        return sp(c).getBoolean("cc_gesture", true);
    }

    public static void setCcGesture(Context c, boolean v) {
        sp(c).edit().putBoolean("cc_gesture", v).apply();
    }

    public static boolean ncGesture(Context c) {
        return sp(c).getBoolean("nc_gesture", true);
    }

    public static void setNcGesture(Context c, boolean v) {
        sp(c).edit().putBoolean("nc_gesture", v).apply();
    }

    public static boolean volHud(Context c) {
        return sp(c).getBoolean("vol_hud", true);
    }

    public static void setVolHud(Context c, boolean v) {
        sp(c).edit().putBoolean("vol_hud", v).apply();
    }

    public static boolean assistive(Context c) {
        return sp(c).getBoolean("assistive", false);
    }

    public static void setAssistive(Context c, boolean v) {
        sp(c).edit().putBoolean("assistive", v).apply();
    }

    public static boolean lockScreen(Context c) {
        return sp(c).getBoolean("lockscreen", false);
    }

    public static void setLockScreen(Context c, boolean v) {
        sp(c).edit().putBoolean("lockscreen", v).apply();
    }

    public static String passcode(Context c) {
        return sp(c).getString("passcode", "");
    }

    public static void setPasscode(Context c, String code) {
        sp(c).edit().putString("passcode", code == null ? "" : code).apply();
    }

    public static Set<String> hiddenApps(Context c) {
        return new HashSet<>(sp(c).getStringSet("hidden_apps", new HashSet<String>()));
    }

    public static void setHiddenApps(Context c, Set<String> pkgs) {
        sp(c).edit().putStringSet("hidden_apps", new HashSet<>(pkgs)).apply();
    }

    public static void setHidden(Context c, String pkg, boolean hidden) {
        Set<String> s = hiddenApps(c);
        if (hidden) s.add(pkg);
        else s.remove(pkg);
        setHiddenApps(c, s);
    }

    /** Dock packages, comma separated, max 4. */
    public static String dockPkgs(Context c) {
        return sp(c).getString("dock_pkgs", "");
    }

    public static void setDockPkgs(Context c, String csv) {
        sp(c).edit().putString("dock_pkgs", csv == null ? "" : csv).apply();
    }

    /** Folders: har entry "name|pkg1,pkg2". */
    public static Set<String> folders(Context c) {
        return new HashSet<>(sp(c).getStringSet("folders", new HashSet<String>()));
    }

    public static void setFolders(Context c, Set<String> folders) {
        sp(c).edit().putStringSet("folders", new HashSet<>(folders)).apply();
    }

    public static String wallpaperAsset(Context c) {
        return sp(c).getString("wallpaper_asset", "wallpapers/wp001.jpg");
    }

    public static void setWallpaperAsset(Context c, String asset) {
        sp(c).edit().putString("wallpaper_asset", asset).apply();
    }

    public static void resetAll(Context c) {
        String wp = wallpaperAsset(c);
        sp(c).edit().clear().apply();
        setWallpaperAsset(c, wp);
    }
}
