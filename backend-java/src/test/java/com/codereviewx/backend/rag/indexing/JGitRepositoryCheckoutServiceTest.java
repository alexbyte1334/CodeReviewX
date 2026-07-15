package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.model.CheckedOutRepository;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JGitRepositoryCheckoutServiceTest {

    @TempDir Path tempDir;

    @Test
    void acceptsOnlyCanonicalGithubRepositoryUris() {
        assertThat(JGitRepositoryCheckoutService.canonicalGithubUri("owner", "repo"))
                .isEqualTo("https://github.com/owner/repo.git");
        assertThat(JGitRepositoryCheckoutService.validateGithubUri("https://github.com/owner/repo"))
                .isEqualTo("https://github.com/owner/repo.git");
        assertThat(JGitRepositoryCheckoutService.validateGithubUri("https://github.com/owner/repo.git/"))
                .isEqualTo("https://github.com/owner/repo.git");

        for (String unsafe : new String[]{
                "http://github.com/o/r", "https://gitlab.com/o/r", "https://token@github.com/o/r",
                "https://github.com/o/r/extra", "https://github.com/o/../r", "https://github.com/o/r?q=x",
                "https://github.com/o/r#x", "https://github.com/o/%2e%2e", "https://github.com/o/r%2fextra"
        }) {
            assertThatThrownBy(() -> JGitRepositoryCheckoutService.validateGithubUri(unsafe))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Repository URL is not allowed");
        }
    }

    @Test
    void checksOutExactCommitDetachedAndCleansUpIdempotently() throws Exception {
        RepositoryFixture fixture = createRepository();
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                tempDir.resolve("work"), 1, fixture.bare().toUri().toString());

        Path checkoutPath;
        try (CheckedOutRepository checkedOut = service.checkout(metadata(fixture.firstSha()))) {
            checkoutPath = checkedOut.path();
            assertThat(checkedOut.commitSha()).isEqualTo(fixture.firstSha());
            assertThat(Files.readString(checkoutPath.resolve("file.txt"))).isEqualTo("first\n");
            try (Git git = Git.open(checkoutPath.toFile())) {
                assertThat(git.getRepository().resolve(Constants.HEAD).name()).isEqualTo(fixture.firstSha());
                assertThat(git.getRepository().getBranch()).isEqualTo(fixture.firstSha());
            }
        }
        assertThat(checkoutPath).doesNotExist();
        service.closeLastCheckoutForTesting();
        assertThat(checkoutPath).doesNotExist();
    }

    @Test
    void fallsBackToFetchingExactShaOutsideInitialDepth() throws Exception {
        RepositoryFixture fixture = createRepository();
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                tempDir.resolve("fallback-work"), 1, fixture.bare().toUri().toString());

        try (CheckedOutRepository checkedOut = service.checkout(metadata(fixture.firstSha()))) {
            assertThat(checkedOut.commitSha()).isEqualTo(fixture.firstSha());
            assertThat(Files.readString(checkedOut.path().resolve("file.txt"))).isEqualTo("first\n");
        }
    }

    @Test
    void failuresCleanTemporaryCheckoutAndNeverLeakToken() throws Exception {
        Path root = tempDir.resolve("failure-work");
        String token = "secret-token-never-print";
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                root, 1, tempDir.resolve("missing.git").toUri().toString(), token);

        assertThatThrownBy(() -> service.checkout(metadata("0123456789012345678901234567890123456789")))
                .hasMessage("Repository checkout failed")
                .message().doesNotContain(token, tempDir.toString());
        assertThat(Files.list(root)).isEmpty();
    }

    private RepositoryFixture createRepository() throws Exception {
        Path source = tempDir.resolve("source-" + System.nanoTime());
        Files.createDirectories(source);
        String first;
        String second;
        try (Git git = Git.init().setDirectory(source.toFile()).call()) {
            Files.writeString(source.resolve("file.txt"), "first\n");
            git.add().addFilepattern("file.txt").call();
            RevCommit firstCommit = git.commit().setMessage("first").setAuthor("test", "test@example.com").call();
            first = firstCommit.name();
            Files.writeString(source.resolve("file.txt"), "second\n");
            git.add().addFilepattern("file.txt").call();
            second = git.commit().setMessage("second").setAuthor("test", "test@example.com").call().name();
        }
        Path bare = tempDir.resolve("bare-" + System.nanoTime() + ".git");
        try (Git ignored = Git.cloneRepository().setURI(source.toUri().toString()).setBare(true)
                .setDirectory(bare.toFile()).call()) {
            ignored.getRepository().getConfig().setBoolean("uploadpack", null, "allowAnySHA1InWant", true);
            ignored.getRepository().getConfig().save();
            return new RepositoryFixture(bare, first, second);
        }
    }

    private GithubPrMetadata metadata(String sha) {
        return new GithubPrMetadata("owner", "repo", 1, "title", "author", "main", "branch",
                sha, sha, "open", "", "", 1, 1, 0);
    }

    private record RepositoryFixture(Path bare, String firstSha, String secondSha) {}
}
