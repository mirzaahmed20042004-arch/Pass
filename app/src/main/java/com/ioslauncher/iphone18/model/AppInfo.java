package com.ioslauncher.iphone18.model;

import android.content.pm.ResolveInfo;

/** Ek installed app ka data. */
public class AppInfo {
    public String label;
    public String packageName;
    public String className;
    public ResolveInfo resolveInfo;
    /** Custom iOS icon resource, ya 0 = original icon mask karke. */
    public int iosIconRes;

    public AppInfo(String label, String packageName, String className, ResolveInfo ri, int iosIconRes) {
        this.label = label;
        this.packageName = packageName;
        this.className = className;
        this.resolveInfo = ri;
        this.iosIconRes = iosIconRes;
    }

    public String componentKey() {
        return packageName + "/" + className;
    }
}
