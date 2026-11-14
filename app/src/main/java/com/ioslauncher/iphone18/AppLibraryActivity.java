package com.ioslauncher.iphone18;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ioslauncher.iphone18.adapter.AppGridAdapter;
import com.ioslauncher.iphone18.model.AppInfo;
import com.ioslauncher.iphone18.model.HomeItem;
import com.ioslauncher.iphone18.util.AppLoader;
import com.ioslauncher.iphone18.util.IconHelper;
import com.ioslauncher.iphone18.util.Prefs;
import com.ioslauncher.iphone18.util.Ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** App Library: auto-categories. */
public class AppLibraryActivity extends AppCompatActivity {

    private final List<String> cats = new ArrayList<>();
    private final Map<String, List<AppInfo>> grouped = new LinkedHashMap<>();
    private final Map<String, AppInfo> byPkg = new java.util.HashMap<>();
    private LibAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_app_library);

        RecyclerView grid = findViewById(R.id.lib_grid);
        grid.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new LibAdapter();
        grid.setAdapter(adapter);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> apps = AppLoader.load(AppLibraryActivity.this, false);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        grouped.clear();
                        cats.clear();
                        byPkg.clear();
                        for (AppInfo a : apps) {
                            byPkg.put(a.packageName, a);
                            String c = AppLoader.categoryOf(a);
                            if (!grouped.containsKey(c)) {
                                grouped.put(c, new ArrayList<AppInfo>());
                                cats.add(c);
                            }
                            grouped.get(c).add(a);
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void openCategory(String cat) {
        List<AppInfo> apps = grouped.get(cat);
        if (apps == null) return;
        View v = getLayoutInflater().inflate(R.layout.dialog_folder, null);
        TextView name = v.findViewById(R.id.folder_name);
        name.setText(cat);
        name.setEnabled(false);
        RecyclerView grid = v.findViewById(R.id.folder_grid);
        grid.setLayoutManager(new GridLayoutManager(this, 4));
        List<HomeItem> items = new ArrayList<>();
        for (AppInfo a : apps) items.add(HomeItem.app(a));
        AppGridAdapter adapter = new AppGridAdapter(this, items, byPkg, new AppGridAdapter.Listener() {
            @Override
            public void onTap(HomeItem item) {
                if (item.kind == HomeItem.APP) {
                    try {
                        LauncherActivity.launchAppStatic(AppLibraryActivity.this, item.app);
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void onLong(HomeItem item) {
            }

            @Override
            public void onBadge(HomeItem item) {
            }
        });
        grid.setAdapter(adapter);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog).setView(v).setPositiveButton("Close", null).show();
    }

    private class LibAdapter extends RecyclerView.Adapter<LibAdapter.LVH> {
        @NonNull
        @Override
        public LVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(AppLibraryActivity.this)
                    .inflate(R.layout.item_library_folder, parent, false);
            return new LVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull LVH h, int position) {
            final String cat = cats.get(position);
            h.name.setText(cat);
            h.minis.removeAllViews();
            List<AppInfo> apps = grouped.get(cat);
            int style = Prefs.iconStyle(AppLibraryActivity.this);
            int px = Ui.dp(AppLibraryActivity.this, 64);
            int show = Math.min(4, apps == null ? 0 : apps.size());
            for (int i = 0; i < show; i++) {
                try {
                    ImageView iv = new ImageView(AppLibraryActivity.this);
                    int s = Ui.dp(AppLibraryActivity.this, 30);
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                    lp.width = s;
                    lp.height = s;
                    lp.setMargins(4, 4, 4, 4);
                    iv.setLayoutParams(lp);
                    Bitmap b = IconHelper.appIcon(AppLibraryActivity.this, apps.get(i), style, px);
                    iv.setImageBitmap(b);
                    h.minis.addView(iv);
                } catch (Exception ignored) {
                }
            }
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openCategory(cat);
                }
            });
        }

        @Override
        public int getItemCount() {
            return cats.size();
        }

        class LVH extends RecyclerView.ViewHolder {
            GridLayout minis;
            TextView name;

            LVH(View v) {
                super(v);
                minis = v.findViewById(R.id.folder_minis);
                name = v.findViewById(R.id.folder_name);
            }
        }
    }
}
