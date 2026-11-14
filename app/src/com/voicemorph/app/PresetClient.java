package com.voicemorph.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Talks to the VoiceMorph backend (GET /api/presets). */
public final class PresetClient {

    public static final class CloudPreset {
        public String name;
        public String author;
        public VoiceEngine.Params params;
    }

    private PresetClient() {
    }

    public static List<CloudPreset> fetch(String baseUrl) throws IOException {
        String url = baseUrl;
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        HttpURLConnection con = (HttpURLConnection) new URL(url + "/api/presets").openConnection();
        con.setConnectTimeout(6000);
        con.setReadTimeout(6000);
        con.setRequestProperty("Accept", "application/json");
        try {
            if (con.getResponseCode() != 200) {
                throw new IOException("http " + con.getResponseCode());
            }
            InputStream in = con.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            JSONArray arr = new JSONArray(new String(bos.toByteArray(), "UTF-8"));
            List<CloudPreset> out = new ArrayList<CloudPreset>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                CloudPreset p = new CloudPreset();
                p.name = o.optString("name", "preset " + i);
                p.author = o.optString("author", "community");
                VoiceEngine.Params pr = new VoiceEngine.Params();
                pr.pitch = (float) o.optDouble("pitch", 1.0);
                pr.strength = (float) o.optDouble("strength", 1.0);
                pr.lowShelfDb = (float) o.optDouble("low_db", 0.0);
                pr.lowShelfHz = (float) o.optDouble("low_hz", 200.0);
                pr.highShelfDb = (float) o.optDouble("high_db", 0.0);
                pr.highShelfHz = (float) o.optDouble("high_hz", 4000.0);
                p.params = pr;
                out.add(p);
            }
            return out;
        } catch (org.json.JSONException e) {
            throw new IOException("bad json: " + e.getMessage());
        } finally {
            con.disconnect();
        }
    }
}
