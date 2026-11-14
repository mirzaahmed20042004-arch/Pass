package com.ioslauncher.iphone18.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import androidx.core.content.ContextCompat;

import com.ioslauncher.iphone18.R;
import com.ioslauncher.iphone18.model.AppInfo;

import java.util.Locale;

/**
 * iOS style squircle icons + Light / Dark / Tinted (iOS 18) themes.
 */
public final class IconHelper {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(160);

    private IconHelper() {}

    /** Mashhoor packages ko iOS icons se map karta hai. */
    public static int mapPackageToIcon(String pkg) {
        if (pkg == null) return 0;
        String p = pkg.toLowerCase(Locale.US);

        if (p.contains("dialer") || p.equals("com.android.phone")
                || p.contains("truecaller") || p.endsWith(".contacts")
                || p.contains("phone")) {
            if (p.contains("contact")) return R.drawable.ic_ios_contacts;
            return R.drawable.ic_ios_phone;
        }
        if (p.contains("mms") || p.contains("messaging") || p.contains("messages")
                || p.contains(" messenger") || p.equals("com.facebook.orca")
                || p.contains("whatsapp") || p.contains("telegram")) {
            return R.drawable.ic_ios_messages;
        }
        if (p.contains("facetime") || p.contains("duo")) return R.drawable.ic_ios_facetime;
        if (p.contains("chrome") || p.contains("browser") || p.contains("firefox")
                || p.contains("opera") || p.contains("brave") || p.contains("samsung") && p.contains("internet")) {
            return R.drawable.ic_ios_safari;
        }
        if (p.contains("gmail") || p.contains("email") || p.contains("mail")
                || p.contains("outlook") || p.contains("yahoo")) {
            return R.drawable.ic_ios_mail;
        }
        if (p.contains("camera") && !p.contains("editor")) return R.drawable.ic_ios_camera;
        if (p.contains("photos") || p.contains("gallery")) return R.drawable.ic_ios_photos;
        if (p.contains("music") || p.contains("spotify") || p.contains("audify")
                || p.contains("soundcloud") || p.contains("youtube") && p.contains("music")) {
            return R.drawable.ic_ios_music;
        }
        if (p.equals("com.android.settings") || p.contains("settings")) return R.drawable.ic_ios_settings;
        if (p.contains("deskclock") || p.contains("clock") || p.contains("alarm")) return R.drawable.ic_ios_clock;
        if (p.contains("weather") || p.contains("accuweather")) return R.drawable.ic_ios_weather;
        if (p.contains("maps") || p.contains("waze") || p.contains("careem")
                || p.contains("uber") || p.contains("navigation")) {
            return R.drawable.ic_ios_maps;
        }
        if (p.contains("notes") || p.contains("keep")) return R.drawable.ic_ios_notes;
        if (p.contains("reminders") || p.contains("todo") || p.contains("tasks")
                || p.contains("ticktick")) {
            return R.drawable.ic_ios_reminders;
        }
        if (p.contains("vending") || p.contains("play") && p.contains("store")
                || p.contains("appstore") || p.contains("galaxy") && p.contains("store")) {
            return R.drawable.ic_ios_appstore;
        }
        if (p.contains("health") || p.contains("fit") || p.contains("pedometer")) return R.drawable.ic_ios_health;
        if (p.contains("wallet") || p.contains("pay") || p.contains("easypaisa")
                || p.contains("jazzcash")) {
            return R.drawable.ic_ios_wallet;
        }
        if (p.contains("calculator") || p.contains("calc")) return R.drawable.ic_ios_calculator;
        if (p.contains("files") || p.contains("filemanager") || p.contains("explorer")) return R.drawable.ic_ios_files;
        if (p.contains("compass")) return R.drawable.ic_ios_compass;
        if (p.contains("calendar")) return R.drawable.ic_ios_calendar;
        return 0;
    }

    /** Apne andar ke iOS mini-apps ke icons. */
    public static int internalIcon(String key) {
        if (key == null) return R.drawable.ic_ios_generic;
        switch (key) {
            case "settings": return R.drawable.ic_ios_settings;
            case "calculator": return R.drawable.ic_ios_calculator;
            case "notes": return R.drawable.ic_ios_notes;
            case "clock": return R.drawable.ic_ios_clock;
            case "weather": return R.drawable.ic_ios_weather;
            case "wallpapers": return R.drawable.ic_ios_photos;
            default: return R.drawable.ic_ios_generic;
        }
    }

    public static void clearCache() {
        CACHE.evictAll();
    }

    /** Final squircle bitmap, style ke saath. */
    public static Bitmap appIcon(Context ctx, AppInfo app, int style, int sizePx) {
        String key = app.packageName + "|" + app.className + "|" + style + "|" + sizePx;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;

        Bitmap out;
        try {
            Drawable d;
            boolean custom = app.iosIconRes != 0;
            if (custom) {
                d = ContextCompat.getDrawable(ctx, app.iosIconRes);
            } else {
                PackageManager pm = ctx.getPackageManager();
                d = app.resolveInfo != null ? app.resolveInfo.loadIcon(pm) : null;
                if (d == null) d = ContextCompat.getDrawable(ctx, R.drawable.ic_ios_generic);
            }
            Bitmap src = drawableToBitmap(d, sizePx);
            if (custom) {
                out = applyStyle(src, style);
                if (src != out) src.recycle();
            } else {
                out = maskToSquircle(src, style);
                src.recycle();
            }
        } catch (Exception e) {
            out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        }
        CACHE.put(key, out);
        return out;
    }

    /** Internal mini-app ka icon. */
    public static Bitmap internalIconBitmap(Context ctx, String key, int style, int sizePx) {
        String ckey = "in:" + key + "|" + style + "|" + sizePx;
        Bitmap cached = CACHE.get(ckey);
        if (cached != null) return cached;
        Drawable d = ContextCompat.getDrawable(ctx, internalIcon(key));
        Bitmap src = drawableToBitmap(d, sizePx);
        Bitmap out = applyStyle(src, style);
        if (src != out) src.recycle();
        CACHE.put(ckey, out);
        return out;
    }

    private static Bitmap drawableToBitmap(Drawable d, int size) {
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null && b.getWidth() == size && b.getHeight() == size) {
                return b.copy(Bitmap.Config.ARGB_8888, false);
            }
        }
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        return bmp;
    }

    /** Light / Dark / Tinted filter. */
    private static Bitmap applyStyle(Bitmap src, int style) {
        if (style == Prefs.ICON_LIGHT) return src;
        ColorMatrix cm = new ColorMatrix();
        if (style == Prefs.ICON_DARK) {
            cm.setScale(0.45f, 0.45f, 0.48f, 1f);
        } else {
            ColorMatrix gray = new ColorMatrix();
            gray.setSaturation(0f);
            ColorMatrix tint = new ColorMatrix(new float[]{
                    0.15f, 0, 0, 0, 20,
                    0, 0.35f, 0, 0, 70,
                    0, 0, 0.7f, 0, 150,
                    0, 0, 0, 1f, 0});
            cm.setConcat(tint, gray);
        }
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }

    /** Original Android icon ko iOS squircle me mask karta hai. */
    private static Bitmap maskToSquircle(Bitmap src, int style) {
        int size = src.getWidth();
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (style == Prefs.ICON_DARK) {
            ColorMatrix cm = new ColorMatrix();
            cm.setScale(0.45f, 0.45f, 0.48f, 1f);
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
        } else if (style == Prefs.ICON_TINTED) {
            ColorMatrix gray = new ColorMatrix();
            gray.setSaturation(0f);
            ColorMatrix tint = new ColorMatrix(new float[]{
                    0.15f, 0, 0, 0, 20,
                    0, 0.35f, 0, 0, 70,
                    0, 0, 0.7f, 0, 150,
                    0, 0, 0, 1f, 0});
            ColorMatrix cm = new ColorMatrix();
            cm.setConcat(tint, gray);
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
        }
        // center-crop source
        int sw = src.getWidth();
        int sh = src.getHeight();
        int side = Math.min(sw, sh);
        Rect srcRect = new Rect((sw - side) / 2, (sh - side) / 2, (sw + side) / 2, (sh + side) / 2);
        Rect dstRect = new Rect(0, 0, size, size);

        // mask layer
        Bitmap mask = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
        Canvas mc = new Canvas(mask);
        Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
        mp.setColor(0xFFFFFFFF);
        mc.drawPath(squirclePath(size), mp);

        Bitmap layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(layer);
        lc.drawBitmap(src, srcRect, dstRect, paint);
        Paint xfer = new Paint(Paint.ANTI_ALIAS_FLAG);
        xfer.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        lc.drawBitmap(mask, 0, 0, xfer);
        mask.recycle();
        c.drawBitmap(layer, 0, 0, null);
        layer.recycle();
        return out;
    }

    /** Package se seedha rounded icon (notifications waghera ke liye). */
    public static Bitmap roundedAppIcon(Context ctx, String pkg, int style, int sizePx) {
        String key = "pkg:" + pkg + "|" + style + "|" + sizePx;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        Bitmap out = null;
        try {
            int custom = mapPackageToIcon(pkg);
            if (custom != 0) {
                Drawable d = ContextCompat.getDrawable(ctx, custom);
                out = applyStyle(drawableToBitmap(d, sizePx), style);
            } else {
                Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
                out = maskToSquircle(drawableToBitmap(d, sizePx), style);
            }
        } catch (Exception ignored) {
        }
        if (out == null) {
            out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        }
        CACHE.put(key, out);
        return out;
    }

    /** Folder ka preview icon: translucent squircle + 4 mini icons. */
    public static Bitmap folderIcon(Context ctx, java.util.List<com.ioslauncher.iphone18.model.AppInfo> members, int style, int sizePx) {
        Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0x59FFFFFF);
        c.drawPath(squirclePath(sizePx), bg);
        int n = Math.min(4, members.size());
        if (n == 0) return out;
        int mini = (int) (sizePx * 0.36f);
        int gap = (int) (sizePx * 0.05f);
        int total = mini * 2 + gap;
        int start = (sizePx - total) / 2;
        for (int i = 0; i < n; i++) {
            try {
                Bitmap m = appIcon(ctx, members.get(i), style, mini);
                int col = i % 2;
                int row = i / 2;
                int x = start + col * (mini + gap);
                int y = start + row * (mini + gap);
                if (n <= 2) y = (sizePx - mini) / 2;
                if (n == 1) x = (sizePx - mini) / 2;
                c.drawBitmap(m, null, new Rect(x, y, x + mini, y + mini), null);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** iOS jaisa squircle path. */
    private static Path squirclePath(int s) {
        float u = s / 100f;
        Path path = new Path();
        path.moveTo(50 * u, 2 * u);
        path.cubicTo(82 * u, 2 * u, 98 * u, 18 * u, 98 * u, 50 * u);
        path.cubicTo(98 * u, 82 * u, 82 * u, 98 * u, 50 * u, 98 * u);
        path.cubicTo(18 * u, 98 * u, 2 * u, 82 * u, 2 * u, 50 * u);
        path.cubicTo(2 * u, 18 * u, 18 * u, 2 * u, 50 * u, 2 * u);
        path.close();
        return path;
    }
}
