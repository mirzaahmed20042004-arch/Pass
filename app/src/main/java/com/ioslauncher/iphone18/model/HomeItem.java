package com.ioslauncher.iphone18.model;

import java.util.ArrayList;
import java.util.List;

/** Home grid ka ek khana: app, folder ya andar ka iOS mini-app. */
public class HomeItem {
    public static final int APP = 0;
    public static final int FOLDER = 1;
    public static final int INTERNAL = 2;

    public int kind;
    public AppInfo app;
    public String folderName;
    public List<String> folderPkgs = new ArrayList<>();
    public String internalKey;
    public String internalLabel;

    public static HomeItem app(AppInfo a) {
        HomeItem h = new HomeItem();
        h.kind = APP;
        h.app = a;
        return h;
    }

    public static HomeItem folder(String name, List<String> pkgs) {
        HomeItem h = new HomeItem();
        h.kind = FOLDER;
        h.folderName = name;
        h.folderPkgs = pkgs;
        return h;
    }

    public static HomeItem internal(String key, String label) {
        HomeItem h = new HomeItem();
        h.kind = INTERNAL;
        h.internalKey = key;
        h.internalLabel = label;
        return h;
    }

    public String label() {
        if (kind == APP && app != null) return app.label;
        if (kind == FOLDER) return folderName;
        if (kind == INTERNAL) return internalLabel;
        return "";
    }
}
