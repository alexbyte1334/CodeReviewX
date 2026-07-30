package com.codereviewx.backend.demo;

final class DemoRedactor {
    private DemoRedactor() {}

    static String sanitize(String value, int maxLength) {
        if (value == null) return null;
        String safe = value
                .replaceAll(
                        "(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^,\\s]+",
                        "$1[redacted]")
                .replaceAll(
                        "(?i)((?:token|api[_-]?key|secret|password)\\s*[:=]\\s*)[^,\\s]+",
                        "$1[redacted]")
                .replaceAll(
                        "(?i)\\b(?:gh[pousr]_[A-Za-z0-9_]+|sk-[A-Za-z0-9_-]{8,})\\b",
                        "[redacted]");
        if (safe.length() <= maxLength) return safe;
        return safe.substring(0, maxLength) + "…";
    }
}
