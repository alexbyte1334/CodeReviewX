package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.model.Language;
import com.codereviewx.backend.rag.model.RepositoryFile;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryFileDiscoveryTest {

    @TempDir Path tempDir;

    @Test
    void discoversStableSafeTextFilesWithLanguagesAndHashes() throws Exception {
        Path root = initRepository();
        write(root, "z/readme.md", "hello");
        write(root, "a/App.java", "class App {}\n");
        write(root, "src/view.tsx", "export const View = () => null;\n");
        write(root, "unknown.custom", "plain text\n");

        List<RepositoryFile> files = discover(root);

        assertThat(files).extracting(RepositoryFile::path)
                .containsExactly("a/App.java", "src/view.tsx", "unknown.custom", "z/readme.md");
        assertThat(files).extracting(RepositoryFile::language)
                .containsExactly(Language.JAVA, Language.TSX, Language.TEXT, Language.MARKDOWN);
        assertThat(files.get(0).contentHash()).matches("[0-9a-f]{64}");
        assertThat(files.get(0).byteSize()).isEqualTo("class App {}\n".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void honorsGitignoreButKeepsTrackedFilesIgnoredLater() throws Exception {
        Path root = initRepository();
        write(root, "tracked.log", "keep\n");
        try (Git git = Git.open(root.toFile())) {
            git.add().addFilepattern("tracked.log").call();
            git.commit().setMessage("tracked").setAuthor("test", "test@example.com").call();
        }
        write(root, ".gitignore", "*.log\nignored/\n");
        write(root, "ignored/untracked.log", "skip\n");
        write(root, "untracked.log", "skip\n");

        assertThat(discover(root)).extracting(RepositoryFile::path)
                .contains("tracked.log", ".gitignore")
                .doesNotContain("ignored/untracked.log", "untracked.log");
    }

    @Test
    void honorsNestedGitignoreNegation() throws Exception {
        Path root = initRepository();
        write(root, "nested/.gitignore", "*.txt\n!important.txt\n");
        write(root, "nested/ignored.txt", "skip\n");
        write(root, "nested/important.txt", "keep\n");
        write(root, "outside.txt", "outside\n");

        assertThat(discover(root)).extracting(RepositoryFile::path)
                .containsExactly("nested/.gitignore", "nested/important.txt", "outside.txt");
    }

    @Test
    void skipsGeneratedSensitiveBinaryOversizeAndInvalidUtf8Files() throws Exception {
        Path root = initRepository();
        write(root, "src/good.py", "ok\n");
        write(root, "node_modules/pkg/index.js", "skip\n");
        write(root, "target/output.txt", "skip\n");
        write(root, "package-lock.json", "{}\n");
        write(root, ".env", "TOKEN=nope\n");
        write(root, "app.min.js", "minified\n");
        Files.write(root.resolve("binary.dat"), new byte[]{'a', 0, 'b'});
        Files.write(root.resolve("invalid.txt"), new byte[]{(byte) 0xc3, (byte) 0x28});
        write(root, "large.txt", "12345678901");

        RepositoryFileDiscovery discovery = new RepositoryFileDiscovery(10, 100, 100);
        assertThat(discovery.discover(checked(root))).extracting(RepositoryFile::path)
                .containsExactly("src/good.py");
    }

    @Test
    void skipsUtf8ControlCharacterBinaryButKeepsUnicodeAndSourceWhitespace() throws Exception {
        Path root = initRepository();
        Files.write(root.resolve("controls.txt"), new byte[]{1, 2, 3});
        write(root, "unicode.txt", "你好\tline\nnext\rform\fend");

        assertThat(discover(root)).extracting(RepositoryFile::path)
                .containsExactly("unicode.txt");
    }

    @Test
    void skipsInternalSymlinksAndRejectsEscapingSymlinks() throws Exception {
        Path root = initRepository();
        write(root, "real.txt", "safe\n");
        try {
            Files.createSymbolicLink(root.resolve("internal-link.txt"), Path.of("real.txt"));
            Files.createSymbolicLink(root.resolve("escape.txt"), tempDir.resolve("outside.txt"));
        } catch (UnsupportedOperationException exception) {
            return;
        }

        assertThatThrownBy(() -> new RepositoryFileDiscovery().discover(checked(root)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Repository contains an unsafe symbolic link");
    }

    @Test
    void enforcesFileAndByteBudgetsDeterministically() throws Exception {
        Path root = initRepository();
        write(root, "a.txt", "12345");
        write(root, "b.txt", "67890");

        assertThatThrownBy(() -> new RepositoryFileDiscovery(100, 1, 100).discover(checked(root)))
                .hasMessage("Repository file budget exceeded");
        assertThatThrownBy(() -> new RepositoryFileDiscovery(100, 10, 9).discover(checked(root)))
                .hasMessage("Repository text budget exceeded");
    }

    @Test
    void skippedCandidatesDoNotConsumeAcceptedFileBudget() throws Exception {
        Path root = initRepository();
        Files.createDirectories(root.resolve(".git/info"));
        Files.writeString(root.resolve(".git/info/exclude"), "ignored-*.txt\n");
        for (int index = 0; index < 10; index++) {
            write(root, ".env.local." + index, "secret");
            write(root, "ignored-" + index + ".txt", "ignored");
            write(root, "large-" + index + ".txt", "x".repeat(11));
            write(root, "node_modules/pkg-" + index + "/index.js", "generated");
            Files.write(root.resolve("binary-" + index + ".dat"), new byte[]{'a', 0, 'b'});
        }
        write(root, "z-valid.txt", "valid");

        assertThat(new RepositoryFileDiscovery(10, 1, 100).discover(checked(root)))
                .extracting(RepositoryFile::path).containsExactly("z-valid.txt");
    }

    @Test
    void skippedCandidatesConsumeIndependentScanEntryBudgetBeforeValidTail() throws Exception {
        Path root = initRepository();
        for (int index = 0; index < 6; index++) {
            write(root, ".env.local." + index, "secret");
        }
        write(root, "z-valid.txt", "must-not-be-reached");

        RepositoryFileDiscovery discovery = new RepositoryFileDiscovery(100, 10, 100, 4, 1_000);

        assertThatThrownBy(() -> discovery.discover(checked(root)))
                .hasMessage("Repository scan entry budget exceeded");
    }

    @Test
    void binaryCandidatesConsumeIndependentScanByteBudgetBeforeValidTail() throws Exception {
        Path root = initRepository();
        for (int index = 0; index < 4; index++) {
            Files.write(root.resolve("binary-" + index + ".dat"), new byte[]{'a', 0, 'b', 'c'});
        }
        write(root, "z-valid.txt", "must-not-be-reached");

        RepositoryFileDiscovery discovery = new RepositoryFileDiscovery(100, 10, 100, 100, 10);

        assertThatThrownBy(() -> discovery.discover(checked(root)))
                .hasMessage("Repository scan byte budget exceeded");
    }

    @Test
    void filtersTrackedCredentialFilesWithoutOvermatchingSafeFiles() throws Exception {
        Path root = initRepository();
        for (String sensitive : List.of(
                ".env.production", ".npmrc", ".pypirc", ".netrc", ".dockercfg", ".git-credentials",
                "certificate.p12", "certificate.pfx", "keystore.jks", "application.keystore")) {
            write(root, sensitive, "secret\n");
        }
        write(root, ".env.example", "TOKEN=\n");
        write(root, ".env.sample", "TOKEN=\n");
        write(root, ".env.template", "TOKEN=\n");
        write(root, "credentials-guide.md", "safe documentation\n");
        write(root, "src/environment.java", "class Environment {}\n");
        write(root, "src/SecretService.java", "class SecretService {}\n");
        write(root, "src/SecretsController.ts", "export class SecretsController {}\n");
        write(root, "src/secretary.go", "package secretary\n");
        write(root, "config/credentials.json", "{}\n");
        write(root, "config/api-token.txt", "token\n");
        write(root, "config/service-account.yaml", "account: x\n");
        try (Git git = Git.open(root.toFile())) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("tracked credentials").setAuthor("test", "test@example.com").call();
        }

        assertThat(discover(root)).extracting(RepositoryFile::path)
                .containsExactly(
                        ".env.example", ".env.sample", ".env.template",
                        "credentials-guide.md", "src/SecretService.java", "src/SecretsController.ts",
                        "src/environment.java", "src/secretary.go");
    }

    @Test
    void defaultsAndEqualLimitsMatchProductionContract() throws Exception {
        RagProperties properties = new RagProperties();
        assertThat(properties.getMaxFileBytes()).isEqualTo(1024L * 1024L);
        assertThat(properties.getMaxFiles()).isEqualTo(5000);
        assertThat(properties.getMaxTextBytes()).isEqualTo(100L * 1024L * 1024L);
        assertThat(properties.getMaxScannedEntries()).isEqualTo(50_000);
        assertThat(properties.getMaxScannedBytes()).isEqualTo(500L * 1024L * 1024L);

        Path fileSizeRoot = initRepository();
        write(fileSizeRoot, "equal.txt", "12345");
        write(fileSizeRoot, "over.txt", "123456");
        assertThat(new RepositoryFileDiscovery(5, 10, 100).discover(checked(fileSizeRoot)))
                .extracting(RepositoryFile::path).containsExactly("equal.txt");

        Path countRoot = initRepository();
        write(countRoot, "a.txt", "a");
        write(countRoot, "b.txt", "b");
        RepositoryFileDiscovery countLimited = new RepositoryFileDiscovery(10, 2, 100);
        assertThat(countLimited.discover(checked(countRoot))).hasSize(2);
        write(countRoot, "c.txt", "c");
        assertThatThrownBy(() -> countLimited.discover(checked(countRoot)))
                .hasMessage("Repository file budget exceeded");

        Path textRoot = initRepository();
        write(textRoot, "a.txt", "1234");
        write(textRoot, "b.txt", "12345");
        RepositoryFileDiscovery textLimited = new RepositoryFileDiscovery(10, 10, 9);
        assertThat(textLimited.discover(checked(textRoot))).hasSize(2);
        write(textRoot, "c.txt", "1");
        assertThatThrownBy(() -> textLimited.discover(checked(textRoot)))
                .hasMessage("Repository text budget exceeded");
    }

    private Path initRepository() throws Exception {
        Path root = tempDir.resolve("repo-" + System.nanoTime());
        Files.createDirectories(root);
        Git.init().setDirectory(root.toFile()).call().close();
        return root;
    }

    private List<RepositoryFile> discover(Path root) {
        return new RepositoryFileDiscovery().discover(checked(root));
    }

    private CheckedOutRepository checked(Path root) {
        return CheckedOutRepository.unmanaged(root, "0123456789012345678901234567890123456789");
    }

    private void write(Path root, String relative, String content) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
