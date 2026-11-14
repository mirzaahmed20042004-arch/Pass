package com.ioslauncher.iphone18.util;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 50 iPhone wallpapers: assets/wallpapers/wp001..wp050.jpg */
public final class WallpaperHelper {

    public static final int COUNT = 50;

    private WallpaperHelper() {}

    public static List<String> allAssets(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            String[] files = ctx.getAssets().list("wallpapers");
            if (files != null) {
                Arrays.sort(files);
                for (String f : files) {
                    if (f.endsWith(".jpg") || f.endsWith(".png") || f.endsWith(".webp")) {
                        out.add("wallpapers/" + f);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        if (out.isEmpty()) {
            for (int i = 1; i <= COUNT; i++) {
                out.add(String.format("wallpapers/wp%03d.jpg", i));
            }
        }
        Collections.sort(out);
        return out;
    }

    public static Bitmap loadSampled(Context ctx, String asset, int reqW, int reqH) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream is = ctx.getAssets().open(asset);
            BitmapFactory.decodeStream(is, null, o);
            is.close();
            o.inSampleSize = calcSample(o.outWidth, o.outHeight, reqW, reqH);
            o.inJustDecodeBounds = false;
            InputStream is2 = ctx.getAssets().open(asset);
            Bitmap b = BitmapFactory.decodeStream(is2, null, o);
            is2.close();
            return b;
        } catch (IOException e) {
            return null;
        }
    }

    private static int calcSample(int w, int h, int reqW, int reqH) {
        int s = 1;
        while ((w / (s * 2)) >= reqW && (h / (s * 2)) >= reqH) s *= 2;
        return s;
    }

    /** System wallpaper set karta hai. which: 0=home, 1=lock, 2=both */
    public static boolean applySystem(Context ctx, String asset, int which) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(ctx);
            int flagHome = WallpaperManager.FLAG_SYSTEM;
            int flagLock = WallpaperManager.FLAG_LOCK;
            if (which == 1) {
                InputStream is = ctx.getAssets().open(asset);
                wm.setStream(is, null, true, flagLock);
                is.close();
            } else if (which == 2) {
                InputStream a = ctx.getAssets().open(asset);
                wm.setStream(a, null, true, flagHome);
                a.close();
                InputStream b = ctx.getAssets().open(asset);
                wm.setStream(b, null, true, flagLock);
                b.close();
            } else {
                InputStream is = ctx.getAssets().open(asset);
                wm.setStream(is, null, true, flagHome);
                is.close();
            }
            Prefs.setWallpaperAsset(ctx, asset);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
