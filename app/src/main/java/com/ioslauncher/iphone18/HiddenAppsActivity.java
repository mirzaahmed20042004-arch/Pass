package com.ioslauncher.iphone18;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ioslauncher.iphone18.model.AppInfo;
import com.ioslauncher.iphone18.util.AppLoader;
import com.ioslauncher.iphone18.util.IconHelper;
import com.ioslauncher.iphone18.util.Prefs;
import com.ioslauncher.iphone18.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Chhupi hui apps wapas lao. */
public class HiddenAppsActivity extends AppCompatActivity {

    private final List<AppInfo> hidden = new ArrayList<>();
    private HAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        Ui.lightStatusBar(this, true);
        setContentView(R.layout.activity_hidden_apps);

        RecyclerView list = findViewById(R.id.hidden_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HAdapter();
        list.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> all = AppLoader.load(HiddenAppsActivity.this, true);
                final Set<String> hs = Prefs.hiddenApps(HiddenAppsActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hidden.clear();
                        for (AppInfo a : all) {
                            if (hs.contains(a.packageName)) hidden.add(a);
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private class HAdapter extends RecyclerView.Adapter<HAdapter.HVH> {
        @NonNull
        @Override
        public HVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(HiddenAppsActivity.this)
                    .inflate(R.layout.item_search_app, parent, false);
            return new HVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull HVH h, int position) {
            final AppInfo app = hidden.get(position);
            h.label.setText(app.label);
            h.label.setTextColor(0xFF000000);
            try {
                Bitmap b = IconHelper.appIcon(HiddenAppsActivity.this, app,
                        Prefs.iconStyle(HiddenAppsActivity.this), Ui.dp(HiddenAppsActivity.this, 88));
                h.icon.setImageBitmap(b);
            } catch (Exception ignored) {
            }
            h.btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setHidden(HiddenAppsActivity.this, app.packageName, false);
                    hidden.remove(app);
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public int getItemCount() {
            return hidden.size();
        }

        class HVH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView label;
            Button btn;

            HVH(View v) {
                super(v);
                icon = v.findViewById(R.id.app_icon);
                label = v.findViewById(R.id.app_label);
                btn = new Button(HiddenAppsActivity.this);
                btn.setText("Unhide");
                btn.setAllCaps(false);
                ((ViewGroup) v).addView(btn);
            }
        }
    }
}
