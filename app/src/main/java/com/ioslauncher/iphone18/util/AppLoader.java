package com.ioslauncher.iphone18.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.ioslauncher.iphone18.model.AppInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Installed apps load + sort karta hai. */
public final class AppLoader {

    private AppLoader() {}

    public static List<AppInfo> load(Context ctx, boolean includeHidden) {
        PackageManager pm = ctx.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(launcher, 0);
        Set<String> hidden = Prefs.hiddenApps(ctx);
        List<AppInfo> out = new ArrayList<>();
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg == null) continue;
            if (pkg.equals(ctx.getPackageName())) continue; // khud ko chhupao
            if (!includeHidden && hidden.contains(pkg)) continue;
            String label = String.valueOf(ri.loadLabel(pm));
            out.add(new AppInfo(label, pkg, ri.activityInfo.name, ri,
                    IconHelper.mapPackageToIcon(pkg)));
        }
        Collections.sort(out, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    /** App Library ke liye simple category. */
    public static String categoryOf(AppInfo app) {
        String p = app.packageName.toLowerCase();
        if (p.contains("whatsapp") || p.contains("telegram") || p.contains("messenger")
                || p.contains("mms") || p.contains("messages") || p.contains("orca")
                || p.contains("instagram") || p.contains("facebook") || p.contains("twitter")
                || p.contains("snapchat") || p.contains("tiktok") || p.contains("linkedin")) {
            return "Social";
        }
        if (p.contains("camera") || p.contains("photo") || p.contains("gallery")
                || p.contains("picsart") || p.contains("snapseed") || p.contains("canva")
                || p.contains("youtube") || p.contains("netflix") || p.contains("video")
                || p.contains("vlc") || p.contains("mxtech")) {
            return "Photo & Video";
        }
        if (p.contains("music") || p.contains("spotify") || p.contains("audify")
                || p.contains("soundcloud") || p.contains("radio")) {
            return "Music";
        }
        if (p.contains("game") || p.contains("pubg") || p.contains("freefire")
                || p.contains("candy") || p.contains("clash") || p.contains("ludo")
                || p.contains("roblox") || p.contains("minecraft")) {
            return "Games";
        }
        if (p.contains("bank") || p.contains("pay") || p.contains("easypaisa")
                || p.contains("jazzcash") || p.contains("wallet") || p.contains("daraz")
                || p.contains("shop") || p.contains("amazon") || p.contains("foodpanda")) {
            return "Shopping & Money";
        }
        if (p.contains("maps") || p.contains("uber") || p.contains("careem")
                || p.contains("travel") || p.contains("airbnb") || p.contains("booking")) {
            return "Travel";
        }
        if (p.contains("mail") || p.contains("gmail") || p.contains("docs")
                || p.contains("drive") || p.contains("office") || p.contains("notes")
                || p.contains("keep") || p.contains("calendar") || p.contains("zoom")
                || p.contains("teams") || p.contains("meet")) {
            return "Productivity";
        }
        if (p.contains("chrome") || p.contains("browser") || p.contains("firefox")
                || p.contains("opera")) {
            return "Utilities";
        }
        return "Others";
    }
}
