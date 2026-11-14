package com.ioslauncher.iphone18.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ioslauncher.iphone18.service.LockService;
import com.ioslauncher.iphone18.service.OverlayService;

/** Reboot ke baad iOS features wapas on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            OverlayService.startIfNeeded(context);
            LockService.startIfNeeded(context);
        } catch (Exception ignored) {
        }
    }
}
