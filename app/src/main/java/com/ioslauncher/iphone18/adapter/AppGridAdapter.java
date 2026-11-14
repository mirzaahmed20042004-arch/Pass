package com.ioslauncher.iphone18.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ioslauncher.iphone18.R;
import com.ioslauncher.iphone18.model.AppInfo;
import com.ioslauncher.iphone18.model.HomeItem;
import com.ioslauncher.iphone18.util.IconHelper;
import com.ioslauncher.iphone18.util.Prefs;
import com.ioslauncher.iphone18.util.Ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Home grid / folder grid ka adapter. */
public class AppGridAdapter extends RecyclerView.Adapter<AppGridAdapter.VH> {

    public interface Listener {
        void onTap(HomeItem item);
        void onLong(HomeItem item);
        void onBadge(HomeItem item);
    }

    private final Context ctx;
    private final List<HomeItem> items;
    private final Listener listener;
    private final Map<String, AppInfo> byPkg;
    private boolean editMode = false;
    private final int iconPx;
    private final int style;

    public AppGridAdapter(Context ctx, List<HomeItem> items, Map<String, AppInfo> byPkg, Listener listener) {
        this.ctx = ctx;
        this.items = items;
        this.byPkg = byPkg != null ? byPkg : new HashMap<String, AppInfo>();
        this.listener = listener;
        this.iconPx = Ui.dp(ctx, 120);
        this.style = Prefs.iconStyle(ctx);
    }

    public void setEditMode(boolean edit) {
        this.editMode = edit;
        notifyDataSetChanged();
    }

    public boolean isEditMode() {
        return editMode;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final HomeItem item = items.get(position);
        Bitmap bmp = null;
        try {
            if (item.kind == HomeItem.APP && item.app != null) {
                bmp = IconHelper.appIcon(ctx, item.app, style, iconPx);
            } else if (item.kind == HomeItem.INTERNAL) {
                bmp = IconHelper.internalIconBitmap(ctx, item.internalKey, style, iconPx);
            } else if (item.kind == HomeItem.FOLDER) {
                bmp = IconHelper.folderIcon(ctx, resolveMembers(item), style, iconPx);
            }
        } catch (Exception ignored) {
        }
        if (bmp != null) h.icon.setImageBitmap(bmp);
        h.label.setText(item.label());
        h.label.setVisibility(Prefs.showLabels(ctx) ? View.VISIBLE : View.GONE);
        h.badge.setVisibility(editMode ? View.VISIBLE : View.GONE);

        if (editMode) {
            RotateAnimation jiggle = new RotateAnimation(-3f, 3f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            jiggle.setDuration(140);
            jiggle.setRepeatMode(Animation.REVERSE);
            jiggle.setRepeatCount(Animation.INFINITE);
            h.icon.startAnimation(jiggle);
        } else {
            h.icon.clearAnimation();
        }

        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onTap(item);
            }
        });
        h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (listener != null) listener.onLong(item);
                return true;
            }
        });
        h.badge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onBadge(item);
            }
        });
    }

    private java.util.List<AppInfo> resolveMembers(HomeItem folder) {
        java.util.List<AppInfo> out = new java.util.ArrayList<>();
        for (String pkg : folder.folderPkgs) {
            AppInfo a = byPkg.get(pkg);
            if (a != null) out.add(a);
        }
        return out;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView label;
        TextView badge;

        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.app_icon);
            label = v.findViewById(R.id.app_label);
            badge = v.findViewById(R.id.badge_hide);
        }
    }
}
