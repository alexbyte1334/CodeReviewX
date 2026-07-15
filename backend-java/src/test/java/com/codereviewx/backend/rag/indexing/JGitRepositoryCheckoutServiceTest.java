package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.review.github.GithubPrMetadata;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JGitRepositoryCheckoutServiceTest {

    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);

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
        Path root = workRoot("work");
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                root, 1, fixture.bare().toUri().toString());

        Path checkoutPath;
        try (CheckedOutRepository checkedOut = service.checkout(metadata(fixture.firstSha()))) {
            checkoutPath = checkedOut.path();
            assertThat(checkedOut.commitSha()).isEqualTo(fixture.firstSha());
            assertThat(Files.readString(checkoutPath.resolve("file.txt"))).isEqualTo("first\n");
            try (Git git = Git.open(checkoutPath.toFile())) {
                assertThat(git.getRepository().resolve(Constants.HEAD).name()).isEqualTo(fixture.firstSha());
                assertThat(git.getRepository().getBranch()).isEqualTo(fixture.firstSha());
            }
            assertOwnerOnly(root);
            assertOwnerOnly(checkoutPath);
        }
        assertThat(checkoutPath).doesNotExist();
        service.closeLastCheckoutForTesting();
        assertThat(checkoutPath).doesNotExist();
    }

    @Test
    void fallsBackToFetchingExactShaOutsideInitialDepth() throws Exception {
        RepositoryFixture fixture = createRepository();
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                workRoot("fallback-work"), 1, fixture.bare().toUri().toString());

        try (CheckedOutRepository checkedOut = service.checkout(metadata(fixture.firstSha()))) {
            assertThat(checkedOut.commitSha()).isEqualTo(fixture.firstSha());
            assertThat(Files.readString(checkedOut.path().resolve("file.txt"))).isEqualTo("first\n");
        }
    }

    @Test
    void failuresCleanTemporaryCheckoutAndNeverLeakToken() throws Exception {
        Path root = workRoot("failure-work");
        String token = "secret-token-never-print";
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                root, 1, tempDir.resolve("missing.git").toUri().toString(), token);

        assertThatThrownBy(() -> service.checkout(metadata("0123456789012345678901234567890123456789")))
                .hasMessage("Repository checkout failed")
                .message().doesNotContain(token, tempDir.toString());
        assertThat(Files.list(root)).isEmpty();
    }

    @Test
    void rejectsShortAndNonHexCommitIdsBeforeCreatingWorkspace() throws Exception {
        Path root = workRoot("invalid-sha-root");
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                root, 1, tempDir.resolve("missing.git").toUri().toString());

        assertThatThrownBy(() -> service.checkout(metadata("abc123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Repository metadata is invalid");
        assertThatThrownBy(() -> service.checkout(metadata("g".repeat(40))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Repository metadata is invalid");
        assertThat(root).doesNotExist();
    }

    @Test
    void rejectsSymlinkedConfiguredRootWithoutCreatingOutside() throws Exception {
        Path outside = workRoot("outside-root");
        Files.createDirectories(outside);
        Path symlink = workRoot("linked-root");
        try {
            Files.createSymbolicLink(symlink, outside);
        } catch (UnsupportedOperationException exception) {
            return;
        }
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                symlink, 1, tempDir.resolve("missing.git").toUri().toString());

        assertThatThrownBy(() -> service.checkout(metadata("0".repeat(40))))
                .hasMessage("Repository checkout failed");
        assertThat(Files.list(outside)).isEmpty();
    }

    @Test
    void rejectsSymlinkedParentBeforeCreatingConfiguredRootOutside() throws Exception {
        Path outside = workRoot("outside-parent");
        Files.createDirectories(outside);
        Path safe = workRoot("safe-parent");
        Files.createDirectories(safe);
        Path link = safe.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException exception) {
            return;
        }
        Path configuredRoot = link.resolve("new-root");
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                configuredRoot, 1, tempDir.resolve("missing.git").toUri().toString());

        assertThatThrownBy(() -> service.checkout(metadata("0".repeat(40))))
                .hasMessage("Repository checkout failed");
        assertThat(outside.resolve("new-root")).doesNotExist();
    }

    @Test
    void publicModelConstructionCannotDeleteAnArbitraryPath() throws Exception {
        Path external = tempDir.resolve("must-remain");
        Files.createDirectories(external);
        Files.writeString(external.resolve("sentinel.txt"), "safe");

        new CheckedOutRepository(external, "0".repeat(40), null).close();

        assertThat(external.resolve("sentinel.txt")).hasContent("safe");
    }

    @Test
    void cleanupRejectsWorkspaceReplacedBySymlinkAndDoesNotDeleteTarget() throws Exception {
        RepositoryFixture fixture = createRepository();
        Path root = workRoot("cleanup-root");
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                root, 1, fixture.bare().toUri().toString());
        CheckedOutRepository checkedOut = service.checkout(metadata(fixture.secondSha()));
        Path workspace = checkedOut.path();
        deleteForTest(workspace);
        Path outside = workRoot("cleanup-outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("sentinel.txt"), "safe");
        try {
            Files.createSymbolicLink(workspace, outside);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        assertThatThrownBy(checkedOut::close).hasMessage("Repository cleanup failed");
        assertThat(outside.resolve("sentinel.txt")).hasContent("safe");
        Files.deleteIfExists(workspace);
    }

    @Test
    void ownerOnlyPermissionsFailClosedOnNonPosixProvider() throws Exception {
        Path archive = tempDir.resolve("non-posix.zip");
        try (FileSystem fileSystem = FileSystems.newFileSystem(
                URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
            Path root = fileSystem.getPath("/work");
            Files.createDirectories(root);

            assertThatThrownBy(() -> JGitRepositoryCheckoutService.requireOwnerOnly(root))
                    .hasMessage("Repository workspace permissions are unsafe");
        }
    }

    @Test
    void creationDetectsAncestorSwapAfterSecureRootOpenWithoutWritingOutside() throws Exception {
        Path root = workRoot("create-race-root");
        Files.createDirectories(root);
        Path movedRoot = workRoot("create-race-original");
        Path outside = workRoot("create-race-outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("sentinel.txt"), "safe");
        JGitRepositoryCheckoutService.WorkspaceBoundaryHook hook = (openedRoot, operation) -> {
            if (operation == JGitRepositoryCheckoutService.BoundaryOperation.CREATE) {
                Files.move(root, movedRoot);
                Files.createSymbolicLink(root, outside);
            }
        };
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                root, 1, tempDir.resolve("missing.git").toUri().toString(), hook);

        assertThatThrownBy(() -> service.checkout(metadata("0".repeat(40))))
                .hasMessage("Repository checkout failed");
        assertThat(Files.list(outside)).extracting(path -> path.getFileName().toString())
                .containsExactly("sentinel.txt");
        assertThat(Files.list(movedRoot)).isEmpty();
        Files.delete(root);
    }

    @Test
    void secureCleanupCannotBeRedirectedByAncestorSwapAfterRootOpen() throws Exception {
        RepositoryFixture fixture = createRepository();
        Path root = workRoot("cleanup-race-root");
        Path movedRoot = workRoot("cleanup-race-original");
        Path outside = workRoot("cleanup-race-outside");
        Files.createDirectories(outside);
        AtomicReference<String> workspaceName = new AtomicReference<>();
        JGitRepositoryCheckoutService.WorkspaceBoundaryHook hook = (openedRoot, operation) -> {
            if (operation == JGitRepositoryCheckoutService.BoundaryOperation.CLEANUP) {
                String name = workspaceName.get();
                Path fakeWorkspace = outside.resolve(name);
                Files.createDirectories(fakeWorkspace);
                Files.writeString(fakeWorkspace.resolve("sentinel.txt"), "safe");
                Files.move(root, movedRoot);
                Files.createSymbolicLink(root, outside);
            }
        };
        JGitRepositoryCheckoutService service = JGitRepositoryCheckoutService.forLocalTesting(
                root, 1, fixture.bare().toUri().toString(), hook);
        CheckedOutRepository checkedOut = service.checkout(metadata(fixture.secondSha()));
        workspaceName.set(checkedOut.path().getFileName().toString());

        checkedOut.close();

        assertThat(movedRoot.resolve(workspaceName.get())).doesNotExist();
        assertThat(outside.resolve(workspaceName.get()).resolve("sentinel.txt")).hasContent("safe");
        Files.delete(root);
    }

    @Test
    void unixProviderOffersSecureDirectoryStreamForCheckoutRoot() throws Exception {
        Path root = workRoot("secure-stream-root");
        Files.createDirectories(root);
        try (var stream = Files.newDirectoryStream(root)) {
            assertThat(stream).isInstanceOf(SecureDirectoryStream.class);
        }
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

    private static void assertOwnerOnly(Path path) throws Exception {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            assertThat(Files.getPosixFilePermissions(path)).isEqualTo(OWNER_ONLY);
        }
    }

    private static void deleteForTest(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private Path workRoot(String name) throws Exception {
        return tempDir.toRealPath().resolve(name);
    }

    private record RepositoryFixture(Path bare, String firstSha, String secondSha) {}
}
