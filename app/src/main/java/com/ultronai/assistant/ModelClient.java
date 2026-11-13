package com.ultronai.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small Ollama-compatible HTTP client. The app never connects until the user configures it. */
final class ModelClient {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private ModelClient() { }

    static JSONObject chat(JSONObject request) {
        HttpURLConnection connection = null;
        try {
            URL url = endpoint(request.optString("url"), "/api/chat");
            String model = validModel(request.optString("model"));
            JSONArray input = request.getJSONArray("messages");
            if (input.length() == 0 || input.length() > 10) throw new IllegalArgumentException("Invalid conversation length.");
            JSONArray messages = new JSONArray();
            int total = 0;
            for (int i = 0; i < input.length(); i++) {
                JSONObject item = input.getJSONObject(i);
                String role = item.optString("role");
                String content = item.optString("content");
                if (!(role.equals("system") || role.equals("user") || role.equals("assistant")) || content.isEmpty() || content.length() > 12000) {
                    throw new IllegalArgumentException("Invalid conversation message.");
                }
                total += content.length();
                if (total > 48000) throw new IllegalArgumentException("Conversation is too long.");
                messages.put(new JSONObject().put("role", role).put("content", content));
            }
            JSONObject payload = new JSONObject().put("model", model).put("messages", messages).put("stream", false);
            connection = open(url, request.optString("token"), "POST", 120000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            int status = connection.getResponseCode();
            String content = read(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) return error(serverError(status, content));
            JSONObject response = new JSONObject(content);
            String text = response.optJSONObject("message") == null ? "" : response.optJSONObject("message").optString("content").trim();
            if (text.isEmpty()) return error("The server returned an empty response.");
            return new JSONObject().put("ok", true).put("text", text.length() > 12000 ? text.substring(0, 12000) : text);
        } catch (Exception ex) {
            return error(safeError(ex));
        } finally { if (connection != null) connection.disconnect(); }
    }

    static JSONObject test(JSONObject request) {
        HttpURLConnection connection = null;
        try {
            URL url = endpoint(request.optString("url"), "/api/tags");
            connection = open(url, request.optString("token"), "GET", 12000);
            int status = connection.getResponseCode();
            String body = read(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) return error(serverError(status, body));
            JSONObject response = new JSONObject(body);
            JSONArray models = response.optJSONArray("models");
            if (models == null) return error("The server did not return an Ollama model list.");
            String wanted = request.optString("model").trim();
            boolean found = false;
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.optJSONObject(i);
                if (model == null) continue;
                String name = model.optString("name", model.optString("model"));
                if (name.equals(wanted) || (!wanted.contains(":") && name.equals(wanted + ":latest"))) found = true;
            }
            JSONObject result = new JSONObject().put("ok", true);
            if (!wanted.isEmpty()) result.put("modelFound", found);
            return result;
        } catch (Exception ex) {
            return error(safeError(ex));
        } finally { if (connection != null) connection.disconnect(); }
    }

    private static URL endpoint(String raw, String path) throws Exception {
        raw = raw.trim();
        if (raw.isEmpty() || raw.length() > 1024) throw new IllegalArgumentException("Enter a valid server URL.");
        URL base = new URL(raw);
        if (!(base.getProtocol().equals("http") || base.getProtocol().equals("https")) || base.getHost().isEmpty()
                || base.getUserInfo() != null || base.getQuery() != null || base.getRef() != null) {
            throw new IllegalArgumentException("Use an HTTP or HTTPS server URL without credentials, query, or fragment.");
        }
        while (raw.endsWith("/")) raw = raw.substring(0, raw.length() - 1);
        return new URL(raw + path);
    }

    private static String validModel(String model) {
        model = model.trim();
        if (model.isEmpty() || model.length() > 120 || !model.matches("[A-Za-z0-9_.:/-]+")) throw new IllegalArgumentException("Enter a valid installed model name.");
        return model;
    }

    private static HttpURLConnection open(URL url, String token, String method, int readTimeout) throws Exception {
        if (token.length() > 256 || token.contains("\r") || token.contains("\n")) throw new IllegalArgumentException("Invalid gateway token.");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false); // Do not leak a token or conversation to a different origin.
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(readTimeout);
        conn.setUseCaches(false);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
        return conn;
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count, total = 0;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) throw new IllegalArgumentException("Server response was too large.");
                out.write(buffer, 0, count);
            }
            return out.toString("UTF-8");
        }
    }

    private static String serverError(int status, String body) {
        // Avoid displaying arbitrary HTML or tokens from a proxy response.
        if (status == 401 || status == 403) return "Server refused access. Check your gateway token.";
        if (status == 404) return "Endpoint or model not found. Check the server URL and installed model.";
        try {
            String detail = new JSONObject(body).optString("error");
            if (!detail.isEmpty() && detail.length() <= 240 && !detail.contains("<")) return "Server error: " + detail;
        } catch (Exception ignored) { }
        return "Server returned HTTP " + status + ".";
    }
    private static String safeError(Exception ex) {
        if (ex instanceof IllegalArgumentException) return ex.getMessage();
        return "Could not reach the server. Check its address, network, and that it is running.";
    }
    private static JSONObject error(String message) {
        try { return new JSONObject().put("ok", false).put("error", message == null ? "Request failed." : message); }
        catch (Exception ignored) { return new JSONObject(); }
    }
}
