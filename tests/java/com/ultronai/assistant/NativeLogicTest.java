package com.ultronai.assistant;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** JVM tests for pure Java Android-side logic, using a real local HTTP server. */
public final class NativeLogicTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        for (String label : new String[]{"Send", "Pay now", "BUY", "delete", "allow", "Confirm", "ＯＫ", "send_message", ""})
            check(!ActionSafety.isSafeLabel(label), "Should block label " + label);
        check(ActionSafety.isSafeLabel("Open menu"), "Ordinary label should be allowed");
        check(ActionSafety.blockedPackage("com.android.settings"), "System settings should be blocked");
        check(ActionSafety.blockedPackage("com.google.android.permissioncontroller"), "Permission controller should be blocked");
        check(!ActionSafety.blockedPackage("com.example.app"), "Ordinary application should not be blocked");

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<JSONObject> received = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicInteger redirectFollowed = new AtomicInteger();
        server.createContext("/api/tags", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"models\":[{\"name\":\"qwen:latest\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/api/chat", exchange -> {
            try {
                JSONObject request = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                received.set(request); authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                if (request.optString("model").equals("redirect")) {
                    exchange.getResponseHeaders().set("Location", "/stolen"); exchange.sendResponseHeaders(302, -1); exchange.close(); return;
                }
                byte[] body = "{\"message\":{\"content\":\"Hello from model\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            } catch (Exception ex) { exchange.sendResponseHeaders(500, -1); exchange.close(); }
        });
        server.createContext("/stolen", exchange -> { redirectFollowed.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            JSONObject request = new JSONObject().put("url", url).put("model", "qwen").put("token", "example-token")
                    .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "hello")))
                    .put("tools", new JSONArray().put("ignored"));
            JSONObject tags = ModelClient.test(request);
            check(tags.optBoolean("ok") && tags.optBoolean("modelFound"), "Model tag test failed: " + tags);
            JSONObject answer = ModelClient.chat(request);
            check(answer.optBoolean("ok") && answer.optString("text").equals("Hello from model"), "Chat failed: " + answer);
            check("Bearer example-token".equals(authorization.get()), "Missing authorization header");
            check(!received.get().optBoolean("stream", true) && !received.get().has("tools"), "Unsafe fields were forwarded");
            request.put("model", "redirect");
            check(!ModelClient.chat(request).optBoolean("ok") && redirectFollowed.get() == 0, "Redirect was followed");
            request.put("model", "bad model");
            check(!ModelClient.chat(request).optBoolean("ok"), "Invalid model accepted");
            request.put("model", "qwen").put("url", "http://user:secret@example.com");
            check(!ModelClient.chat(request).optBoolean("ok"), "Embedded credentials accepted");
            System.out.println("Native logic tests passed: model HTTP, redirect protection, and accessibility filters.");
        } finally { server.stop(0); }
    }
}
