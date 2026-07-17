package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewStaticAnalysisServiceTest {

    private final ReviewStaticAnalysisService service = new ReviewStaticAnalysisService();

    @Test
    void analyze_detectsSecretLikeRequestParameterInAddedDiffLine() {
        GithubPrDiff diff = new GithubPrDiff(
                "diff --git a/src/App.ts b/src/App.ts\n"
                        + "@@ -1 +1 @@\n"
                        + "+const password = request.query.password;\n",
                1,
                92,
                false,
                List.of()
        );

        List<ReviewFinding> findings = service.analyze(diff, RepositoryContextIndexResult.empty());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getSource()).isEqualTo(IssueSource.SEMGREP);
        assertThat(findings.get(0).getFilePath()).isEqualTo("src/App.ts");
        assertThat(findings.get(0).getStartLine()).isEqualTo(1);
        assertThat(findings.get(0).getTitle()).contains("Secret-like request parameter");
    }

    @Test
    void analyze_detectsUnpinnedPackageDependencyFromRepositoryContext() {
        RepositoryContextIndexResult context = new RepositoryContextIndexResult(
                List.of(new RepositoryContextFile(
                        "frontend/package.json",
                        "json",
                        64,
                        false,
                        "{\n  \"dependencies\": {\n    \"left-pad\": \"latest\"\n  }\n}\n"
                )),
                1,
                64,
                false,
                "context"
        );

        List<ReviewFinding> findings = service.analyze(null, context);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getSource()).isEqualTo(IssueSource.DEPENDENCY);
        assertThat(findings.get(0).getFilePath()).isEqualTo("frontend/package.json");
        assertThat(findings.get(0).getTitle()).contains("Unpinned npm dependency");
    }

    @Test
    void analyze_detectsUnpinnedPackageDependencyAcrossJsonLines() {
        String content = "{\n  \"dependencies\": {\n    \"left-pad\"\n      :\n      \"latest\"\n  }\n}\n";
        RepositoryContextIndexResult context = new RepositoryContextIndexResult(
                List.of(new RepositoryContextFile(
                        "package.json", "json", content.length(), false, content)),
                1, content.length(), false, "");

        List<ReviewFinding> findings = service.analyze(null, context);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.getSource()).isEqualTo(IssueSource.DEPENDENCY);
            assertThat(finding.getStartLine()).isEqualTo(3);
        });
    }

    @Test
    void analyze_detectsAddedPackageAndMavenDependencyRisksWithoutLegacyContext() {
        GithubPrDiff diff = new GithubPrDiff("""
                diff --git a/frontend/package.json b/frontend/package.json
                @@ -1 +1 @@
                +{"dependencies":{"unsafe":"latest","wildcard":"*"}}
                diff --git a/backend/pom.xml b/backend/pom.xml
                @@ -10 +10 @@
                +<version>2.0-SNAPSHOT</version>
                """, 2, 180, false, List.of());

        List<ReviewFinding> findings = service.analyze(diff, RepositoryContextIndexResult.empty());

        assertThat(findings).extracting(ReviewFinding::getSource)
                .containsExactly(IssueSource.DEPENDENCY, IssueSource.DEPENDENCY);
        assertThat(findings).extracting(ReviewFinding::getFilePath)
                .containsExactly("frontend/package.json", "backend/pom.xml");
    }

    @Test
    void analyze_ignoresRemovedSafeAndNonManifestDependencyText() {
        GithubPrDiff diff = new GithubPrDiff("""
                diff --git a/frontend/package.json b/frontend/package.json
                @@ -1,2 +1,2 @@
                -{"unsafe":"latest"}
                +{"safe":"1.2.3"}
                diff --git a/docs/example.txt b/docs/example.txt
                @@ -1 +1 @@
                +{"unsafe":"latest"}<version>2.0-SNAPSHOT</version>
                """, 2, 180, false, List.of());

        assertThat(service.analyze(diff, RepositoryContextIndexResult.empty())).isEmpty();
    }

    @Test
    void analyze_doesNotDuplicateChangedManifestAlreadyCoveredByLegacyContext() {
        String content = "{\"dependencies\":{\"unsafe\":\"latest\"}}";
        RepositoryContextIndexResult context = new RepositoryContextIndexResult(
                List.of(new RepositoryContextFile("package.json", "json", content.length(), false, content)),
                1, content.length(), false, "context");
        GithubPrDiff diff = new GithubPrDiff("""
                diff --git a/package.json b/package.json
                @@ -1 +1 @@
                +{"dependencies":{"unsafe": "latest"}}
                """, 1, 80, false, List.of());

        assertThat(service.analyze(diff, context)).hasSize(1)
                .allSatisfy(finding -> assertThat(finding.getSource()).isEqualTo(IssueSource.DEPENDENCY));
    }
}
