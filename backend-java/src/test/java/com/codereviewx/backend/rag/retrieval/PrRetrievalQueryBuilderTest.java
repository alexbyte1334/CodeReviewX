package com.codereviewx.backend.rag.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrRetrievalQueryBuilderTest {

    private final PrRetrievalQueryBuilder builder = new PrRetrievalQueryBuilder();

    @Test
    void usesOnlyBoundedPrSignalsAndRemovesPatchMarkersAndDuplicates() {
        PrRetrievalQueryBuilder.PrQuery input = new PrRetrievalQueryBuilder.PrQuery(
                "Fix account authorization",
                List.of("src/AuthService.java", "src/AuthService.java"),
                List.of("@@ -10,2 +10,4 @@ public void authorize()"),
                List.of("authorize", "authorize"),
                List.of("+if (allowed) {", "-if (allowed) {", "+++ b/src/AuthService.java",
                        "diff --git a/src/AuthService.java b/src/AuthService.java", " context();"));

        String query = builder.build(input);

        assertThat(query).contains("Fix account authorization", "src/AuthService.java",
                "@@ -10,2 +10,4 @@ public void authorize()", "authorize", "if (allowed) {", "context();");
        assertThat(query).doesNotContain("+++ b/", "diff --git", "+if", "-if");
        assertThat(count(query, "src/AuthService.java")).isEqualTo(1);
        assertThat(count(query, "if (allowed) {")).isEqualTo(1);
    }

    @Test
    void redactsHighEntropySecretsAndNeverExceedsEightThousandCharacters() {
        String secret = "ghp_A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8";
        PrRetrievalQueryBuilder.PrQuery input = new PrRetrievalQueryBuilder.PrQuery(
                "Rotate " + secret,
                List.of("src/Secrets.java"),
                List.of("@@ -1 +1 @@"),
                List.of("loadSecret"),
                java.util.stream.IntStream.range(0, 2_000)
                        .mapToObj(index -> "+changed line " + index + " " + "x".repeat(80))
                        .toList());

        String query = builder.build(input);

        assertThat(query).contains("[REDACTED]").doesNotContain(secret);
        assertThat(query.length()).isLessThanOrEqualTo(8_000);
    }

    @Test
    void redactsHighEntropyAlphabeticMixedCaseTokensWithoutRedactingLowEntropyIdentifiers() {
        String alphabeticSecret = "AbCdEfGhIjKlMnOpQrStUvWxYzAbCdEf";
        String ordinaryIdentifier = "repositoryContextConfigurationFactory";

        String query = builder.build(new PrRetrievalQueryBuilder.PrQuery(
                "Rotate " + alphabeticSecret, List.of("src/" + ordinaryIdentifier + ".java"),
                List.of(), List.of(ordinaryIdentifier), List.of()));

        assertThat(query).contains("[REDACTED]", ordinaryIdentifier).doesNotContain(alphabeticSecret);
    }

    @Test
    void redactsHighEntropyAllUppercaseTokensWithoutRedactingUppercaseConstants() {
        String alphabeticSecret = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEF";
        String ordinaryConstant = "MAX_RETRY_COUNT_CONSTANT";

        String query = builder.build(new PrRetrievalQueryBuilder.PrQuery(
                "Rotate " + alphabeticSecret, List.of("src/" + ordinaryConstant + ".java"),
                List.of(), List.of(ordinaryConstant), List.of()));

        assertThat(query).contains("[REDACTED]", ordinaryConstant).doesNotContain(alphabeticSecret);
    }

    @Test
    void failsClosedForNullAndPathologicalValues() {
        assertThat(builder.build(null)).isEmpty();
        assertThat(builder.build(new PrRetrievalQueryBuilder.PrQuery(null, null, null, null, null))).isEmpty();

        String query = builder.build(new PrRetrievalQueryBuilder.PrQuery(
                "\u0000".repeat(20_000), List.of(), List.of(), List.of(), List.of()));

        assertThat(query.length()).isLessThanOrEqualTo(8_000);
        assertThat(query).doesNotContain("\u0000");
    }

    private static int count(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
