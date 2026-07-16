package com.codereviewx.backend.rag.security;

import java.util.regex.Pattern;

public final class RagSecurityPolicy {
    private static final Pattern TOKEN = Pattern.compile("(?i)\\b(?:sk-[a-z0-9_-]{12,}|gh[pousr]_[a-z0-9_]{20,}|bearer\\s+[a-z0-9._~-]{16,})\\b");
    private RagSecurityPolicy() {}
    public static String redact(String value) { return value == null ? null : TOKEN.matcher(value).replaceAll("[REDACTED]"); }
    public static boolean isSensitivePath(String path) {
        String p = path == null ? "" : path.replace('\\','/').toLowerCase();
        String name = p.substring(p.lastIndexOf('/') + 1);
        return name.equals(".env") || name.matches(".*(credentials|credential|secret|private[_-]?key).*" )
                || name.endsWith(".pem") || name.endsWith(".key") || name.endsWith(".p12") || name.endsWith(".pfx");
    }
}
