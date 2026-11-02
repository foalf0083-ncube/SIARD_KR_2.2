// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd.utils;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class CmdI18n {

    private static final String BASE_NAME = "ch.admin.bar.siard2.cmd.i18n.messages";

    private static volatile ResourceBundle bundle = load(Locale.getDefault());

    private CmdI18n() {
    }

    private static ResourceBundle load(Locale locale) {
        try {
            return ResourceBundle.getBundle(BASE_NAME, locale);
        } catch (MissingResourceException e) {
            return ResourceBundle.getBundle(BASE_NAME, Locale.ENGLISH);
        }
    }

    public static void setLocale(Locale locale) {
        bundle = load(locale);
    }

    public static String get(String key, Object... args) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            pattern = key;
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        StringBuilder sb = new StringBuilder(pattern.length() + 32);
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '{') {
                int end = pattern.indexOf('}', i);
                if (end > i) {
                    String inner = pattern.substring(i + 1, end);
                    int idx = parseIndex(inner);
                    if (idx >= 0) {
                        sb.append(idx < args.length ? String.valueOf(args[idx]) : "");
                        i = end + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int parseIndex(String s) {
        if (s.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return -1;
            }
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
