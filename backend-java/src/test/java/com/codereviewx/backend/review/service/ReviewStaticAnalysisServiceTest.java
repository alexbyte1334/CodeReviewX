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
}
