package com.ioslauncher.iphone18;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ioslauncher.iphone18.util.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** iOS Notes (offline). */
public class NotesActivity extends AppCompatActivity {

    private final List<Note> notes = new ArrayList<>();
    private NAdapter adapter;

    private static class Note {
        long id;
        String body;
        long updated;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        Ui.lightStatusBar(this, true);
        setContentView(R.layout.activity_notes);

        RecyclerView list = findViewById(R.id.notes_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.btn_new_note).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editNote(-1);
            }
        });
        load();
    }

    private void load() {
        notes.clear();
        try {
            String json = getSharedPreferences("ios_launcher_prefs", MODE_PRIVATE)
                    .getString("notes_json", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Note n = new Note();
                n.id = o.optLong("id", System.currentTimeMillis() + i);
                n.body = o.optString("body", "");
                n.updated = o.optLong("updated", System.currentTimeMillis());
                if (!n.body.trim().isEmpty()) notes.add(n);
            }
        } catch (Exception ignored) {
        }
        adapter.notifyDataSetChanged();
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Note n : notes) {
                JSONObject o = new JSONObject();
                o.put("id", n.id);
                o.put("body", n.body);
                o.put("updated", n.updated);
                arr.put(o);
            }
            getSharedPreferences("ios_launcher_prefs", MODE_PRIVATE)
                    .edit().putString("notes_json", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void editNote(final int index) {
        final EditText input = new EditText(this);
        input.setMinLines(6);
        input.setGravity(android.view.Gravity.TOP);
        input.setHint("New note…");
        if (index >= 0) input.setText(notes.get(index).body);
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setTitle(index >= 0 ? "Edit Note" : "New Note")
                .setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String body = input.getText().toString().trim();
                        if (body.isEmpty()) return;
                        if (index >= 0) {
                            notes.get(index).body = body;
                            notes.get(index).updated = System.currentTimeMillis();
                        } else {
                            Note n = new Note();
                            n.id = System.currentTimeMillis();
                            n.body = body;
                            n.updated = System.currentTimeMillis();
                            notes.add(0, n);
                        }
                        save();
                        adapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton("Cancel", null);
        if (index >= 0) {
            b.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    notes.remove(index);
                    save();
                    adapter.notifyDataSetChanged();
                }
            });
        }
        b.show();
    }

    private class NAdapter extends RecyclerView.Adapter<NAdapter.NVH> {
        @NonNull
        @Override
        public NVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(NotesActivity.this).inflate(R.layout.item_note, parent, false);
            return new NVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull NVH h, final int position) {
            Note n = notes.get(position);
            String[] lines = n.body.split("\n");
            h.title.setText(lines.length > 0 ? lines[0] : "Note");
            String when = new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                    .format(new Date(n.updated));
            h.snippet.setText(when + "  " + (lines.length > 1 ? lines[1] : n.body));
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editNote(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return notes.size();
        }

        class NVH extends RecyclerView.ViewHolder {
            TextView title;
            TextView snippet;

            NVH(View v) {
                super(v);
                title = v.findViewById(R.id.note_title);
                snippet = v.findViewById(R.id.note_snippet);
            }
        }
    }
}
