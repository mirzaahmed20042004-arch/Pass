package com.ioslauncher.iphone18;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ioslauncher.iphone18.util.Prefs;
import com.ioslauncher.iphone18.util.Ui;
import com.ioslauncher.iphone18.util.WallpaperHelper;

import java.util.List;

/** 50 iPhone wallpapers. */
public class WallpaperPickerActivity extends AppCompatActivity {

    private List<String> assets;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_wallpaper_picker);

        assets = WallpaperHelper.allAssets(this);
        RecyclerView grid = findViewById(R.id.wp_grid);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        grid.setAdapter(new WpAdapter());
    }

    private void preview(final String asset) {
        try {
            View v = getLayoutInflater().inflate(R.layout.dialog_wallpaper_preview, null);
            final ImageView img = v.findViewById(R.id.preview_image);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Bitmap b = WallpaperHelper.loadSampled(WallpaperPickerActivity.this, asset, 540, 1170);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (b != null) img.setImageBitmap(b);
                        }
                    });
                }
            }).start();
            final AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog).setView(v).create();
            v.findViewById(R.id.btn_set_home).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    applyWp(asset, 0);
                    d.dismiss();
                }
            });
            v.findViewById(R.id.btn_set_both).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    applyWp(asset, 2);
                    d.dismiss();
                }
            });
            d.show();
        } catch (Exception ignored) {
        }
    }

    private void applyWp(final String asset, final int which) {
        Toast.makeText(this, "Setting wallpaper…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = WallpaperHelper.applySystem(WallpaperPickerActivity.this, asset, which);
                Prefs.setWallpaperAsset(WallpaperPickerActivity.this, asset);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(WallpaperPickerActivity.this,
                                ok ? "Wallpaper set ✓" : "Couldn't set wallpaper",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private class WpAdapter extends RecyclerView.Adapter<WpAdapter.WVH> {
        @NonNull
        @Override
        public WVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(WallpaperPickerActivity.this)
                    .inflate(R.layout.item_wallpaper, parent, false);
            return new WVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull final WVH h, int position) {
            final String asset = assets.get(position);
            h.thumb.setImageBitmap(null);
            h.thumb.setTag(asset);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Bitmap b = WallpaperHelper.loadSampled(WallpaperPickerActivity.this, asset, 240, 520);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (asset.equals(h.thumb.getTag()) && b != null) {
                                h.thumb.setImageBitmap(b);
                            }
                        }
                    });
                }
            }).start();
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    preview(asset);
                }
            });
        }

        @Override
        public int getItemCount() {
            return assets.size();
        }

        class WVH extends RecyclerView.ViewHolder {
            ImageView thumb;

            WVH(View v) {
                super(v);
                thumb = v.findViewById(R.id.wp_thumb);
            }
        }
    }
}
