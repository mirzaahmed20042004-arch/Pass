package com.ultronai.assistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

/** Explicit, on-demand accessibility only: no event logging or background automation. */
public class AssistService extends AccessibilityService {
    private static volatile AssistService instance;
    @Override protected void onServiceConnected() { super.onServiceConnected(); instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* Never record events. */ }
    @Override public void onInterrupt() { }
    @Override public boolean onUnbind(android.content.Intent intent) { if (instance == this) instance = null; return super.onUnbind(intent); }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }
    static boolean isConnected() { return instance != null; }

    static boolean isSafeLabel(String label) { return ActionSafety.isSafeLabel(label); }
    private static boolean blockedPackage(CharSequence name) { return ActionSafety.blockedPackage(name); }
    static String snapshot() {
        AssistService service = instance;
        if (service == null) return "Screen assistance is off. Enable Ultron Ai in Android Accessibility settings first.";
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return "This app did not expose a readable screen. Try another app or screen.";
        if (service.getPackageName().contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
            root.recycle(); return "Open another app, then long-press the HUD to read its visible labels.";
        }
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        Set<String> lines = new LinkedHashSet<>();
        queue.add(root); int visited = 0; int length = 0;
        while (!queue.isEmpty() && visited++ < 400 && lines.size() < 60 && length < 6000) {
            AccessibilityNodeInfo node = queue.removeFirst();
            try {
                if (!node.isVisibleToUser() || node.isPassword()) continue;
                String text = node.getText() == null ? "" : node.getText().toString().trim();
                String description = node.getContentDescription() == null ? "" : node.getContentDescription().toString().trim();
                for (String value : new String[]{text, description}) {
                    value = value.replaceAll("[\\r\\n]+", " ").trim();
                    if (!value.isEmpty() && value.length() <= 300 && lines.add(value)) length += value.length();
                }
                for (int i = 0; i < node.getChildCount() && queue.size() < 400; i++) {
                    AccessibilityNodeInfo child = node.getChild(i); if (child != null) queue.addLast(child);
                }
            } finally { node.recycle(); }
        }
        while (!queue.isEmpty()) queue.removeFirst().recycle();
        if (lines.isEmpty()) return "No readable labels were exposed by this screen.";
        StringBuilder output = new StringBuilder();
        for (String line : lines) { if (output.length() > 0) output.append('\n'); output.append("• ").append(line); }
        return output.toString();
    }

    static String tapExactLabel(String label) {
        AssistService service = instance;
        if (service == null) return "Screen assistance is no longer enabled.";
        if (!isSafeLabel(label)) return "That action is blocked for safety.";
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return "No accessible screen was found.";
        if (blockedPackage(root.getPackageName())) { root.recycle(); return "Open a non-system target app before the countdown ends."; }
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(); queue.add(root);
        AccessibilityNodeInfo match = null; int count = 0, visited = 0; boolean truncated = false;
        while (!queue.isEmpty() && visited++ < 400) {
            AccessibilityNodeInfo node = queue.removeFirst();
            try {
                if (!node.isVisibleToUser() || node.isPassword() || node.isEditable()) continue;
                String text = node.getText() == null ? "" : node.getText().toString().trim();
                String desc = node.getContentDescription() == null ? "" : node.getContentDescription().toString().trim();
                if ((text.equalsIgnoreCase(label) || desc.equalsIgnoreCase(label)) && node.isEnabled()) {
                    count++; if (count == 1) match = AccessibilityNodeInfo.obtain(node);
                }
                for (int i = 0; i < node.getChildCount(); i++) {
                    if (queue.size() >= 400) { truncated = true; break; }
                    AccessibilityNodeInfo child = node.getChild(i); if (child != null) queue.addLast(child);
                }
            } finally { node.recycle(); }
        }
        if (!queue.isEmpty()) truncated = true;
        while (!queue.isEmpty()) queue.removeFirst().recycle();
        if (truncated) { if (match != null) match.recycle(); return "This screen is too complex to verify a unique match; no tap was made."; }
        if (count != 1 || match == null) {
            if (match != null) match.recycle();
            return count > 1 ? "More than one matching label was found; no tap was made." : "No matching visible label was found; no tap was made.";
        }
        AccessibilityNodeInfo current = match;
        try {
            for (int depth = 0; current != null && depth < 6; depth++) {
                if (current.isPassword() || current.isEditable() || !current.isEnabled() || !current.isVisibleToUser() || blockedPackage(current.getPackageName())) return "This target cannot be tapped safely.";
                CharSequence text = current.getText(), desc = current.getContentDescription();
                if ((text != null && !text.toString().trim().isEmpty() && !isSafeLabel(text.toString())) || (desc != null && !desc.toString().trim().isEmpty() && !isSafeLabel(desc.toString()))) return "This action was blocked for safety.";
                if (current.isClickable()) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK) ? "One tap was performed." : "The app did not accept the tap.";
                AccessibilityNodeInfo parent = current.getParent(); current.recycle(); current = parent;
            }
            return "That label was not attached to a clickable control.";
        } finally { if (current != null) current.recycle(); }
    }
}
