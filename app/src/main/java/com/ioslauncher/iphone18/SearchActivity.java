package com.ioslauncher.iphone18;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ioslauncher.iphone18.model.AppInfo;
import com.ioslauncher.iphone18.model.HomeItem;
import com.ioslauncher.iphone18.util.AppLoader;
import com.ioslauncher.iphone18.util.IconHelper;
import com.ioslauncher.iphone18.util.Prefs;
import com.ioslauncher.iphone18.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Spotlight search: neeche swipe par khulti hai. */
public class SearchActivity extends AppCompatActivity {

    private EditText box;
    private RecyclerView results;
    private final List<HomeItem> all = new ArrayList<>();
    private final List<HomeItem> shown = new ArrayList<>();
    private RowAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_search);

        box = findViewById(R.id.search_box);
        results = findViewById(R.id.results);
        results.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RowAdapter();
        results.setAdapter(adapter);

        findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        box.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> apps = AppLoader.load(SearchActivity.this, false);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        all.clear();
                        all.add(HomeItem.internal("settings", "Settings"));
                        all.add(HomeItem.internal("calculator", "Calculator"));
                        all.add(HomeItem.internal("notes", "Notes"));
                        all.add(HomeItem.internal("clock", "Clock"));
                        all.add(HomeItem.internal("weather", "Weather"));
                        all.add(HomeItem.internal("wallpapers", "Wallpapers"));
                        for (AppInfo a : apps) all.add(HomeItem.app(a));
                        filter(box.getText().toString());
                    }
                });
            }
        }).start();
    }

    private void filter(String q) {
        shown.clear();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.US);
        for (HomeItem h : all) {
            if (query.isEmpty() || h.label().toLowerCase(Locale.US).contains(query)) {
                shown.add(h);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void open(HomeItem h) {
        try {
            hideKeyboard();
            if (h.kind == HomeItem.APP && h.app != null) {
                LauncherActivity.launchAppStatic(this, h.app);
            } else if (h.kind == HomeItem.INTERNAL) {
                if (h.internalKey.equals("settings")) startActivity(new android.content.Intent(this, SettingsActivity.class));
                else if (h.internalKey.equals("calculator")) startActivity(new android.content.Intent(this, CalculatorActivity.class));
                else if (h.internalKey.equals("notes")) startActivity(new android.content.Intent(this, NotesActivity.class));
                else if (h.internalKey.equals("clock")) startActivity(new android.content.Intent(this, ClockActivity.class));
                else if (h.internalKey.equals("weather")) startActivity(new android.content.Intent(this, WeatherActivity.class));
                else if (h.internalKey.equals("wallpapers")) startActivity(new android.content.Intent(this, WallpaperPickerActivity.class));
            }
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Can't open app", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    private class RowAdapter extends RecyclerView.Adapter<RowAdapter.RVH> {
        @NonNull
        @Override
        public RVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(SearchActivity.this).inflate(R.layout.item_search_app, parent, false);
            return new RVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RVH h, int position) {
            final HomeItem item = shown.get(position);
            h.label.setText(item.label());
            try {
                int style = Prefs.iconStyle(SearchActivity.this);
                int px = Ui.dp(SearchActivity.this, 88);
                Bitmap b;
                if (item.kind == HomeItem.APP && item.app != null) {
                    b = IconHelper.appIcon(SearchActivity.this, item.app, style, px);
                } else {
                    b = IconHelper.internalIconBitmap(SearchActivity.this, item.internalKey, style, px);
                }
                h.icon.setImageBitmap(b);
            } catch (Exception ignored) {
            }
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    open(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        class RVH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView label;

            RVH(View v) {
                super(v);
                icon = v.findViewById(R.id.app_icon);
                label = v.findViewById(R.id.app_label);
            }
        }
    }
}
