package com.voicemorph.app;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Saved morphed clips live in app-private external storage (no storage permission needed). */
public final class ClipLibrary {

    public static final class Clip {
        public File file;
        public String name;
        public float seconds;
        public int sampleRate;
    }

    private ClipLibrary() {
    }

    public static File dir(Context context) {
        File d = context.getExternalFilesDir("library");
        if (d == null) {
            d = new File(context.getFilesDir(), "library");
        }
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static List<Clip> list(Context context) {
        File[] files = dir(context).listFiles();
        List<Clip> clips = new ArrayList<Clip>();
        if (files == null) return clips;
        for (File f : files) {
            if (!f.getName().endsWith(".wav")) continue;
            Clip c = new Clip();
            c.file = f;
            String base = f.getName().substring(0, f.getName().length() - 4);
            int idx = base.lastIndexOf('_');
            c.name = idx > 0 ? base.substring(0, idx).replace('_', ' ') : base;
            c.seconds = 0f;
            try {
                WavFile.Data d = WavFile.read(f);
                c.seconds = d.pcm.length / (float) d.sampleRate;
                c.sampleRate = d.sampleRate;
            } catch (IOException e) {
                c.seconds = f.length() / 88200f;
                c.sampleRate = 44100;
            }
            clips.add(c);
        }
        final List<Clip> sorted = clips;
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        List<Clip> out = new ArrayList<Clip>();
        for (File f : files) {
            for (Clip c : sorted) {
                if (c.file.equals(f)) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }

    public static File save(Context context, short[] pcm, int sampleRate, String label) throws IOException {
        String safe = label.replaceAll("[^A-Za-z0-9]+", "_").toLowerCase();
        if (safe.isEmpty()) safe = "clip";
        File f = new File(dir(context), safe + "_" + System.currentTimeMillis() + ".wav");
        WavFile.write(f, pcm, sampleRate);
        return f;
    }

    public static boolean delete(Clip clip) {
        return clip.file.delete();
    }
}
