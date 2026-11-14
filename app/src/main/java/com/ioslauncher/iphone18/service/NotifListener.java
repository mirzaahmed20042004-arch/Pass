package com.ioslauncher.iphone18.service;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** System notifications pakad kar iOS style me dikhata hai. */
public class NotifListener extends NotificationListenerService {

    public static class Item {
        public String key;
        public String pkg;
        public String appLabel;
        public String title;
        public String text;
        public long time;
        public android.app.PendingIntent tap;
    }

    public interface Listener {
        void onNotifsChanged();
    }

    private static final List<Item> ITEMS = new ArrayList<>();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static NotifListener instance;

    public static void addListener(Listener l) {
        if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    public static List<Item> snapshot() {
        synchronized (ITEMS) {
            return new ArrayList<>(ITEMS);
        }
    }

    public static void clearOne(String key) {
        try {
            if (instance != null) instance.cancelNotification(key);
        } catch (Exception ignored) {
        }
    }

    public static void clearAll() {
        try {
            if (instance != null) instance.cancelAllNotifications();
        } catch (Exception ignored) {
        }
    }

    public static boolean isEnabled(Context ctx) {
        try {
            String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
            if (TextUtils.isEmpty(flat)) return false;
            ComponentName me = new ComponentName(ctx, NotifListener.class);
            return flat.contains(me.flattenToString());
        } catch (Exception e) {
            return false;
        }
    }

    public static void openSettings(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onListenerConnected() {
        refresh();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        refresh();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        refresh();
    }

    private void refresh() {
        try {
            StatusBarNotification[] actives = getActiveNotifications();
            List<Item> list = new ArrayList<>();
            if (actives != null) {
                for (StatusBarNotification sbn : actives) {
                    if (sbn == null || sbn.getNotification() == null) continue;
                    String pkg = sbn.getPackageName();
                    if (TextUtils.isEmpty(pkg)) continue;
                    if (pkg.equals(getPackageName())) continue;
                    if ((sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;
                    Bundle ex = sbn.getNotification().extras;
                    CharSequence t = ex != null ? ex.getCharSequence(Notification.EXTRA_TITLE) : null;
                    CharSequence tx = ex != null ? ex.getCharSequence(Notification.EXTRA_TEXT) : null;
                    if (tx == null && ex != null) tx = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
                    if (TextUtils.isEmpty(t) && TextUtils.isEmpty(tx)) continue;
                    Item it = new Item();
                    it.key = sbn.getKey();
                    it.pkg = pkg;
                    try {
                        it.appLabel = String.valueOf(getPackageManager().getApplicationLabel(
                                getPackageManager().getApplicationInfo(pkg, 0)));
                    } catch (Exception e) {
                        it.appLabel = pkg;
                    }
                    it.title = t == null ? "" : t.toString();
                    it.text = tx == null ? "" : tx.toString();
                    it.time = sbn.getPostTime();
                    it.tap = sbn.getNotification().contentIntent;
                    list.add(it);
                }
            }
            synchronized (ITEMS) {
                ITEMS.clear();
                ITEMS.addAll(list);
            }
            for (Listener l : LISTENERS) {
                try {
                    l.onNotifsChanged();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
