// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd.utils;

public final class IdQuoting {

    private IdQuoting() {
    }

    public static String safeId(String formatted) {
        if (formatted == null || formatted.isEmpty()) return formatted;
        if (isAscii(formatted)) return formatted;
        char c0 = formatted.charAt(0);
        if (c0 == '"' || c0 == '`' || c0 == '[') return formatted;
        return quote(formatted);
    }

    public static String safeQualified(String schema, String name) {
        String s = schema == null || schema.isEmpty() ? null : part(schema);
        String n = part(name);
        return s == null ? n : s + "." + n;
    }

    private static String part(String id) {
        if (id == null) return null;
        if (id.isEmpty()) return quote(id);
        char c0 = id.charAt(0);
        if (c0 == '"' || c0 == '`' || c0 == '[') return id; // 이미 인용됨
        return quote(id);
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 128) return false;
        }
        return true;
    }

    private static String quote(String id) {
        return '"' + id.replace("\"", "\"\"") + '"';
    }
}
