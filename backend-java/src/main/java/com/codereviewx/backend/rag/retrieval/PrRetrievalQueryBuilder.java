package com.codereviewx.backend.rag.retrieval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrRetrievalQueryBuilder {

    public static final int MAX_QUERY_CHARS = 8_000;
    private static final int MAX_VALUES_PER_SECTION = 400;
    private static final int MAX_VALUE_CHARS = 2_000;
    private static final Pattern SECRET_CANDIDATE = Pattern.compile("[A-Za-z0-9_+/=-]{20,}");
    private static final Pattern KNOWN_SECRET = Pattern.compile(
            "(?i)(?:gh[pousr]_[A-Za-z0-9]{20,}|AKIA[A-Z0-9]{16}|sk-[A-Za-z0-9_-]{20,})");

    public String build(PrQuery input) {
        if (input == null) {
            return "";
        }
        Set<String> values = new LinkedHashSet<>();
        add(values, input.title(), false);
        addAll(values, input.changedPaths(), false);
        addAll(values, input.diffHunkHeaders(), false);
        addAll(values, input.changedSymbols(), false);
        addAll(values, input.changedLines(), true);

        StringBuilder query = new StringBuilder(Math.min(MAX_QUERY_CHARS, values.size() * 80));
        for (String value : values) {
            if (query.length() > 0 && query.length() < MAX_QUERY_CHARS) {
                query.append('\n');
            }
            int remaining = MAX_QUERY_CHARS - query.length();
            if (remaining <= 0) {
                break;
            }
            query.append(value, 0, Math.min(value.length(), remaining));
        }
        return query.toString();
    }

    private static void addAll(Set<String> values, List<String> candidates, boolean patchLine) {
        if (candidates == null) {
            return;
        }
        int count = Math.min(candidates.size(), MAX_VALUES_PER_SECTION);
        for (int index = 0; index < count; index++) {
            add(values, candidates.get(index), patchLine);
        }
    }

    private static void add(Set<String> values, String candidate, boolean patchLine) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        String bounded = candidate.substring(0, Math.min(candidate.length(), MAX_VALUE_CHARS));
        String normalized = stripControls(bounded).trim();
        if (patchLine) {
            if (normalized.startsWith("diff --git ") || normalized.startsWith("index ")
                    || normalized.startsWith("--- ") || normalized.startsWith("+++ ")
                    || normalized.equals("\\ No newline at end of file")) {
                return;
            }
            if (!normalized.isEmpty() && (normalized.charAt(0) == '+' || normalized.charAt(0) == '-')) {
                normalized = normalized.substring(1).trim();
            }
        }
        normalized = redact(normalized);
        if (!normalized.isBlank()) {
            values.add(normalized);
        }
    }

    private static String stripControls(String value) {
        StringBuilder clean = new StringBuilder(value.length());
        value.codePoints().filter(codePoint -> codePoint == '\t' || codePoint >= 32)
                .forEach(clean::appendCodePoint);
        return clean.toString();
    }

    private static String redact(String value) {
        String knownRedacted = KNOWN_SECRET.matcher(value).replaceAll("[REDACTED]");
        Matcher matcher = SECRET_CANDIDATE.matcher(knownRedacted);
        StringBuffer redacted = new StringBuffer(knownRedacted.length());
        while (matcher.find()) {
            String token = matcher.group();
            matcher.appendReplacement(redacted, isHighEntropy(token) ? "[REDACTED]" : Matcher.quoteReplacement(token));
        }
        matcher.appendTail(redacted);
        return redacted.toString();
    }

    private static boolean isHighEntropy(String token) {
        boolean hasLetter = token.chars().anyMatch(Character::isLetter);
        boolean hasDigit = token.chars().anyMatch(Character::isDigit);
        if (!hasLetter) {
            return false;
        }
        int[] frequencies = new int[128];
        int measured = 0;
        int uppercase = 0;
        int letters = 0;
        int caseTransitions = 0;
        Boolean previousUppercase = null;
        for (char character : token.toCharArray()) {
            char normalized = Character.toLowerCase(character);
            if (normalized < frequencies.length) {
                frequencies[normalized]++;
                measured++;
            }
            if (Character.isLetter(character)) {
                boolean currentUppercase = Character.isUpperCase(character);
                letters++;
                if (currentUppercase) {
                    uppercase++;
                }
                if (previousUppercase != null && previousUppercase != currentUppercase) {
                    caseTransitions++;
                }
                previousUppercase = currentUppercase;
            }
        }
        if (measured == 0) {
            return false;
        }
        double entropy = 0.0;
        int distinct = 0;
        for (int frequency : frequencies) {
            if (frequency > 0) {
                distinct++;
                double probability = (double) frequency / measured;
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
        }
        double diversity = (double) distinct / measured;
        double uppercaseRatio = (double) uppercase / letters;
        double transitionRatio = letters < 2 ? 0.0 : (double) caseTransitions / (letters - 1);
        boolean mixedCaseRandom = uppercaseRatio >= 0.25 && uppercaseRatio <= 0.75
                && transitionRatio >= 0.2 && diversity >= 0.5;
        boolean singleCaseRandom = (uppercase == 0 || uppercase == letters)
                && entropy >= 4.2 && diversity >= 0.65;
        return entropy >= 3.5 && (hasDigit || mixedCaseRandom || singleCaseRandom);
    }

    public record PrQuery(String title, List<String> changedPaths, List<String> diffHunkHeaders,
                          List<String> changedSymbols, List<String> changedLines) {
    }
}
