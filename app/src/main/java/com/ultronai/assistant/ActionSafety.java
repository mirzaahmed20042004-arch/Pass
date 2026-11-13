package com.ultronai.assistant;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative filters, not a guarantee that a control is harmless. */
final class ActionSafety {
    private static final Pattern SENSITIVE = Pattern.compile("(^|[^a-z0-9])(send|submit|post|publish|pay|payment|buy|purchase|order|transfer|delete|remove|erase|install|uninstall|allow|permit|grant|confirm|approve|authorize|accept|agree|continue|proceed|ok|call|dial|password|passcode|pin|otp|sign[ -]?in|log[ -]?in)([^a-z0-9]|$)");
    private ActionSafety() { }
    static boolean isSafeLabel(String label) {
        if (label == null) return false;
        String normalized = Normalizer.normalize(label.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return !normalized.isEmpty() && normalized.length() <= 80 && !SENSITIVE.matcher(normalized).find();
    }
    static boolean blockedPackage(CharSequence name) {
        if (name == null) return true;
        String pkg = name.toString();
        return pkg.equals("com.ultronai.assistant") || pkg.equals("android") || pkg.startsWith("com.android.settings")
                || pkg.startsWith("com.google.android.settings") || pkg.equals("com.android.systemui")
                || pkg.contains("permissioncontroller") || pkg.contains("packageinstaller");
    }
}
