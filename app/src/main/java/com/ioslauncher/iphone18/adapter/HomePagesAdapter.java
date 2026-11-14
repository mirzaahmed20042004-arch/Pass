package com.ioslauncher.iphone18.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ioslauncher.iphone18.R;
import com.ioslauncher.iphone18.model.AppInfo;
import com.ioslauncher.iphone18.model.HomeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Home ki horizontal pages; har page me grid. */
public class HomePagesAdapter extends RecyclerView.Adapter<HomePagesAdapter.PageVH> {

    private final Context ctx;
    private final List<List<HomeItem>> pages = new ArrayList<>();
    private final Map<String, AppInfo> byPkg;
    private final AppGridAdapter.Listener listener;
    private final int cols;
    private boolean editMode = false;
    private final List<AppGridAdapter> children = new ArrayList<>();

    public HomePagesAdapter(Context ctx, List<HomeItem> all, int cols, int rows,
                            Map<String, AppInfo> byPkg, AppGridAdapter.Listener listener) {
        this.ctx = ctx;
        this.cols = cols;
        this.byPkg = byPkg;
        this.listener = listener;
        int perPage = Math.max(1, cols * rows);
        for (int i = 0; i < all.size(); i += perPage) {
            pages.add(new ArrayList<>(all.subList(i, Math.min(all.size(), i + perPage))));
        }
        if (pages.isEmpty()) pages.add(new ArrayList<HomeItem>());
    }

    public int pageCount() {
        return pages.size();
    }

    public void setEditMode(boolean edit) {
        this.editMode = edit;
        for (AppGridAdapter a : children) a.setEditMode(edit);
    }

    @NonNull
    @Override
    public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_home_page, parent, false);
        RecyclerView grid = (RecyclerView) v;
        grid.setLayoutManager(new GridLayoutManager(ctx, cols));
        return new PageVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PageVH holder, int position) {
        AppGridAdapter adapter = new AppGridAdapter(ctx, pages.get(position), byPkg, listener);
        adapter.setEditMode(editMode);
        holder.grid.setAdapter(adapter);
        if (!children.contains(adapter)) children.add(adapter);
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class PageVH extends RecyclerView.ViewHolder {
        RecyclerView grid;

        PageVH(View v) {
            super(v);
            grid = (RecyclerView) v;
        }
    }
}
