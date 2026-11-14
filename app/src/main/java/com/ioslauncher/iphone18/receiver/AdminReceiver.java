package com.ioslauncher.iphone18.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Lock Screen se phone lock karne ke liye device admin. */
public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
    }
}
