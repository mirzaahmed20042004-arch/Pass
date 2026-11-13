package com.ultronai.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int VOICE_REQUEST = 1201;
    private static final int MIC_REQUEST = 1202;
    private static final int NOTIFICATION_REQUEST = 1203;
    private static final String ASSET_ROOT = "file:///android_asset/www/";
    private static volatile String pendingSnapshot;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newFixedThreadPool(2);
    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean pageReady;
    private boolean pendingHudStart;
    private boolean voiceCancelled;
    private boolean tapPending;

    static void queueSnapshot(String text) { pendingSnapshot = text; }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff070e1b);
        getWindow().setNavigationBarColor(0xff070e1b);
        web = new WebView(this);
        web.setBackgroundColor(0xff070e1b);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true); // Required for bundled android_asset resources.
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        if (Build.VERSION.SDK_INT >= 21) settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(), "UltronAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return handleNavigation(url); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return handleNavigation(request.getUrl().toString()); }
            @Override public void onPageFinished(WebView view, String url) {
                if (url.startsWith(ASSET_ROOT)) { pageReady = true; sendCapabilities(); deliverSnapshot(); }
            }
        });
        setContentView(web);
        web.loadUrl(ASSET_ROOT + "index.html");
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady && tts != null) {
                tts.setLanguage(Locale.getDefault());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { }
                    @Override public void onDone(String utteranceId) { callback("onSpeechDone"); }
                    @Override public void onError(String utteranceId) { callback("onVoiceError", "Text-to-speech is unavailable on this device."); }
                });
            }
        });
    }

    private boolean handleNavigation(String url) {
        if (url.startsWith(ASSET_ROOT)) return false;
        try {
            Uri uri = Uri.parse(url);
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        } catch (Exception ignored) { }
        return true; // Never expose the JavaScript bridge to an external page.
    }

    @Override protected void onResume() {
        super.onResume();
        if (pendingHudStart) {
            pendingHudStart = false;
            if (canOverlay()) startHudWithNotificationPermission();
            else callback("onActionResult", "HUD permission was not granted.");
        }
        sendCapabilities();
        deliverSnapshot();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); deliverSnapshot(); }
    private void deliverSnapshot() {
        if (!pageReady || pendingSnapshot == null) return;
        String text = pendingSnapshot; pendingSnapshot = null; callback("onScreenSnapshot", text);
    }
    private boolean canOverlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }
    private JSONObject capabilities() {
        JSONObject result = new JSONObject();
        try {
            result.put("native", true).put("sdk", Build.VERSION.SDK_INT).put("overlayAllowed", canOverlay())
                    .put("hudRunning", HudService.isRunning()).put("accessibilityEnabled", AssistService.isConnected())
                    .put("voiceAvailable", SpeechRecognizer.isRecognitionAvailable(this));
        } catch (Exception ignored) { }
        return result;
    }
    private void sendCapabilities() { callback("onCapabilities", capabilities().toString()); }
    private void callback(String function, Object... args) {
        main.post(() -> {
            if (!pageReady || web == null || isFinishing()) return;
            StringBuilder script = new StringBuilder("window.UltronApp&&window.UltronApp.").append(function).append("(");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) script.append(',');
                Object arg = args[i];
                if (arg instanceof Number || arg instanceof Boolean) script.append(arg.toString());
                else script.append(JSONObject.quote(arg == null ? "" : arg.toString()));
            }
            script.append(");");
            web.evaluateJavascript(script.toString(), null);
        });
    }

    private void startHudWithNotificationPermission() {
        // Android 13 can hide the HUD's stop notification unless the user opts in.
        // The HUD still works if this optional permission is declined.
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        } else startHudService();
    }
    private void startHudService() {
        try {
            Intent intent = new Intent(this, HudService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
            main.postDelayed(this::sendCapabilities, 500);
        } catch (Exception ex) { callback("onActionResult", "Could not start the floating HUD on this device."); }
    }
    private void beginVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            callback("onVoiceError", "No speech recognition service is available on this phone."); return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST); return;
        }
        voiceCancelled = false;
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Ultron Ai");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (ActivityNotFoundException ex) { callback("onVoiceError", "Install or enable a speech recognition service first."); }
        catch (Exception ex) { callback("onVoiceError", "Could not start voice recognition."); }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) beginVoice();
            else callback("onVoiceError", "Microphone permission was not granted.");
        } else if (requestCode == NOTIFICATION_REQUEST) {
            startHudService(); // Notification access is optional; the app can still turn the HUD off.
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST && !voiceCancelled) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) callback("onVoiceResult", results.get(0));
                else callback("onVoiceError", "No speech was recognized.");
            } else callback("onVoiceError", "Voice recognition stopped.");
        }
    }
    @Override public void onBackPressed() {
        if (web == null || !pageReady) { super.onBackPressed(); return; }
        web.evaluateJavascript("window.UltronApp && window.UltronApp.onBackPressed ? window.UltronApp.onBackPressed() : false", result -> {
            if (!"true".equals(result)) finish();
        });
    }
    @Override protected void onDestroy() {
        pageReady = false;
        if (tts != null) { tts.stop(); tts.shutdown(); }
        network.shutdownNow();
        if (web != null) { web.removeJavascriptInterface("UltronAndroid"); web.destroy(); web = null; }
        super.onDestroy();
    }

    public final class Bridge {
        @JavascriptInterface public String getCapabilities() { return capabilities().toString(); }
        @JavascriptInterface public void startVoice() { main.post(MainActivity.this::beginVoice); }
        @JavascriptInterface public void stopVoice() { main.post(() -> { voiceCancelled = true; finishActivity(VOICE_REQUEST); if (tts != null) tts.stop(); }); }
        @JavascriptInterface public void speak(String text) {
            main.post(() -> {
                if (!ttsReady || tts == null) { callback("onVoiceError", "Text-to-speech is not ready on this phone."); return; }
                String safe = text == null ? "" : text.substring(0, Math.min(text.length(), 3500));
                if (tts.speak(safe, TextToSpeech.QUEUE_FLUSH, null, "ultron-reply") == TextToSpeech.ERROR) callback("onVoiceError", "Could not play the spoken reply.");
            });
        }
        @JavascriptInterface public void startHud() {
            main.post(() -> {
                if (canOverlay()) { startHudWithNotificationPermission(); return; }
                try {
                    pendingHudStart = true;
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                } catch (Exception ex) { pendingHudStart = false; callback("onActionResult", "Open Android settings to allow display over other apps."); }
            });
        }
        @JavascriptInterface public void stopHud() { main.post(() -> { stopService(new Intent(MainActivity.this, HudService.class)); main.postDelayed(MainActivity.this::sendCapabilities, 250); }); }
        @JavascriptInterface public void openAccessibilitySettings() {
            main.post(() -> {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch (Exception ex) { callback("onActionResult", "Open Accessibility in Android settings to enable Ultron."); }
            });
        }
        @JavascriptInterface public void scheduleTap(String label) {
            main.post(() -> {
                String target = label == null ? "" : label.trim();
                if (!AssistService.isConnected()) { callback("onActionResult", "Enable screen assistance in Accessibility settings first."); return; }
                if (!AssistService.isSafeLabel(target)) { callback("onActionResult", "That label is empty or blocked as a sensitive action."); return; }
                if (tapPending) { callback("onActionResult", "A tap is already pending; wait for it to finish."); return; }
                tapPending = true;
                Toast.makeText(MainActivity.this, "Switch to the target app now. One tap in 5 seconds.", Toast.LENGTH_LONG).show();
                main.postDelayed(() -> {
                    tapPending = false;
                    String result = AssistService.tapExactLabel(target);
                    Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
                    callback("onActionResult", result);
                }, 5000);
            });
        }
        @JavascriptInterface public void searchWeb(String query) {
            main.post(() -> {
                if (query == null || query.trim().isEmpty()) return;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query.trim().substring(0, Math.min(query.trim().length(), 1000)))))); }
                catch (Exception ex) { callback("onActionResult", "No browser is available to open the search."); }
            });
        }
        @JavascriptInterface public void openApp(String name) { main.post(() -> openInstalledApp(name)); }
        @JavascriptInterface public void draftMessage(String json) {
            main.post(() -> {
                try {
                    JSONObject data = new JSONObject(json);
                    String recipient = data.optString("recipient").trim();
                    String body = data.optString("body").trim();
                    if (!recipient.matches("[+0-9() .-]{0,40}") || body.isEmpty() || body.length() > 2000) {
                        callback("onActionResult", "Use a phone number, or leave it blank to choose a recipient."); return;
                    }
                    String digits = recipient.replaceAll("[^+0-9]", "");
                    Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(digits)));
                    intent.putExtra("sms_body", body);
                    startActivity(intent);
                } catch (ActivityNotFoundException ex) { callback("onActionResult", "No messaging app is available on this phone."); }
                catch (Exception ex) { callback("onActionResult", "Could not open the message draft."); }
            });
        }
        @JavascriptInterface public void shareText(String text) {
            main.post(() -> {
                if (text == null || text.trim().isEmpty()) return;
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text.substring(0, Math.min(text.length(), 3000)));
                    startActivity(Intent.createChooser(intent, "Share with"));
                } catch (Exception ex) { callback("onActionResult", "No app is available to share this draft."); }
            });
        }
        @JavascriptInterface public void chatWithModel(String json) { runModelRequest(json, true); }
        @JavascriptInterface public void testModel(String json) { runModelRequest(json, false); }
    }
    private void runModelRequest(String json, boolean chat) {
        network.execute(() -> {
            long id = -1;
            try {
                JSONObject request = new JSONObject(json);
                id = request.optLong("id", -1);
                JSONObject result = chat ? ModelClient.chat(request) : ModelClient.test(request);
                callback(chat ? "onModelResponse" : "onModelTest", id, result.toString());
            } catch (Exception ex) {
                callback(chat ? "onModelResponse" : "onModelTest", id, "{\"ok\":false,\"error\":\"Invalid request.\"}");
            }
        });
    }
    private void openInstalledApp(String requested) {
        String query = requested == null ? "" : requested.trim();
        if (query.isEmpty() || query.length() > 100) { callback("onActionResult", "Enter an app name."); return; }
        try {
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> matches = getPackageManager().queryIntentActivities(launcher, 0);
            List<ResolveInfo> exact = new ArrayList<>(); List<ResolveInfo> partial = new ArrayList<>();
            for (ResolveInfo info : matches) {
                String label = String.valueOf(info.loadLabel(getPackageManager()));
                if (label.equalsIgnoreCase(query)) exact.add(info);
                else if (label.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) partial.add(info);
            }
            List<ResolveInfo> chosen = exact.isEmpty() ? partial : exact;
            if (chosen.isEmpty()) { callback("onActionResult", "No installed app matched “" + query + "”."); return; }
            if (chosen.size() > 1) { callback("onActionResult", "Multiple apps matched. Try the exact app name."); return; }
            ResolveInfo info = chosen.get(0);
            Intent open = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            open.setClassName(info.activityInfo.packageName, info.activityInfo.name);
            startActivity(open);
        } catch (Exception ex) { callback("onActionResult", "Could not open that app on this phone."); }
    }
}
