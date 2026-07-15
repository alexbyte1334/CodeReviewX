package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.model.CheckedOutRepository;
import com.codereviewx.backend.rag.model.Language;
import com.codereviewx.backend.rag.model.RepositoryFile;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public final class RepositoryFileDiscovery {

    private static final long DEFAULT_MAX_FILE_BYTES = 1024L * 1024L;
    private static final int DEFAULT_MAX_FILES = 5000;
    private static final long DEFAULT_MAX_TEXT_BYTES = 100L * 1024L * 1024L;
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", "node_modules", "dist", "build", "target", "vendor");
    private static final Set<String> SKIPPED_FILES = Set.of(
            ".env", "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "pnpm-lock.yml",
            "composer.lock", "cargo.lock", "gemfile.lock", "poetry.lock");
    private static final Map<String, Language> LANGUAGES = languages();

    private final long maxFileBytes;
    private final int maxFiles;
    private final long maxTextBytes;

    public RepositoryFileDiscovery() {
        this(DEFAULT_MAX_FILE_BYTES, DEFAULT_MAX_FILES, DEFAULT_MAX_TEXT_BYTES);
    }

    @Autowired
    public RepositoryFileDiscovery(RagProperties properties) {
        this(properties.getMaxFileBytes(), properties.getMaxFiles(), properties.getMaxTextBytes());
    }

    RepositoryFileDiscovery(long maxFileBytes, int maxFiles, long maxTextBytes) {
        if (maxFileBytes <= 0 || maxFiles <= 0 || maxTextBytes <= 0) {
            throw new IllegalArgumentException("Repository discovery limits must be positive");
        }
        this.maxFileBytes = maxFileBytes;
        this.maxFiles = maxFiles;
        this.maxTextBytes = maxTextBytes;
    }

    public List<RepositoryFile> discover(CheckedOutRepository checkedOut) {
        Path root = checkedOut.path().toAbsolutePath().normalize();
        try {
            root.toRealPath();
            Set<String> ignored = ignoredUntrackedPaths(checkedOut, root);
            List<Path> candidates = collectCandidates(root);
            candidates.sort(Comparator.comparing(path -> relative(root, path)));

            List<RepositoryFile> files = new ArrayList<>();
            long totalBytes = 0;
            for (Path candidate : candidates) {
                String relative = relative(root, candidate);
                if (isIgnored(relative, ignored) || shouldSkipFile(candidate.getFileName().toString())) {
                    continue;
                }
                long size = Files.size(candidate);
                if (size > maxFileBytes) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(candidate);
                String content = decodeUtf8(bytes);
                if (content == null || containsNul(content)) {
                    continue;
                }
                if (files.size() >= maxFiles) {
                    throw new IllegalStateException("Repository file budget exceeded");
                }
                if (totalBytes + bytes.length > maxTextBytes) {
                    throw new IllegalStateException("Repository text budget exceeded");
                }
                files.add(new RepositoryFile(relative, language(relative), content, bytes.length, Hashing.sha256(bytes)));
                totalBytes += bytes.length;
            }
            return List.copyOf(files);
        } catch (UnsafeSymbolicLinkException exception) {
            throw new IllegalStateException("Repository contains an unsafe symbolic link");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Repository file discovery failed");
        }
    }

    private static List<Path> collectCandidates(Path root) throws IOException {
        List<Path> candidates = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (!directory.equals(root) && SKIPPED_DIRECTORIES.contains(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink()) {
                    Path linkTarget = Files.readSymbolicLink(file);
                    Path target = (linkTarget.isAbsolute() ? linkTarget : file.getParent().resolve(linkTarget))
                            .toAbsolutePath().normalize();
                    if (!target.startsWith(root)) {
                        throw new UnsafeSymbolicLinkException();
                    }
                    return FileVisitResult.CONTINUE;
                }
                if (attributes.isRegularFile()) {
                    candidates.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return candidates;
    }

    private static Set<String> ignoredUntrackedPaths(CheckedOutRepository checkedOut, Path root) {
        Repository repository = checkedOut.repository();
        boolean closeRepository = false;
        try {
            if (repository == null) {
                repository = new FileRepositoryBuilder().findGitDir(root.toFile()).build();
                closeRepository = true;
            }
            Status status = Git.wrap(repository).status().call();
            return new HashSet<>(status.getIgnoredNotInIndex());
        } catch (Exception exception) {
            throw new IllegalStateException("Repository ignore rules could not be evaluated");
        } finally {
            if (closeRepository && repository != null) {
                repository.close();
            }
        }
    }

    private static boolean isIgnored(String path, Set<String> ignored) {
        for (String ignoredPath : ignored) {
            String normalized = ignoredPath.replace('\\', '/');
            if (path.equals(normalized) || path.startsWith(normalized.endsWith("/") ? normalized : normalized + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldSkipFile(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return SKIPPED_FILES.contains(lower)
                || lower.endsWith(".min.js")
                || lower.endsWith(".min.css")
                || lower.endsWith(".pem")
                || lower.endsWith(".key")
                || lower.equals("id_rsa")
                || lower.equals("id_ed25519");
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            return null;
        }
    }

    private static boolean containsNul(String content) {
        return content.indexOf('\0') >= 0;
    }

    private static String relative(Path root, Path path) {
        String relative = root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
        if (relative.isBlank() || relative.startsWith("/") || relative.equals("..")
                || relative.startsWith("../") || relative.contains("/../")) {
            throw new IllegalStateException("Repository path is unsafe");
        }
        return relative;
    }

    private static Language language(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        if (fileName.equals("dockerfile") || fileName.equals("makefile")) {
            return Language.SHELL;
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? Language.TEXT : LANGUAGES.getOrDefault(fileName.substring(dot), Language.TEXT);
    }

    private static Map<String, Language> languages() {
        Map<String, Language> languages = new HashMap<>();
        languages.put(".java", Language.JAVA);
        languages.put(".ts", Language.TYPESCRIPT);
        languages.put(".tsx", Language.TSX);
        languages.put(".js", Language.JAVASCRIPT);
        languages.put(".jsx", Language.JSX);
        languages.put(".py", Language.PYTHON);
        languages.put(".go", Language.GO);
        languages.put(".rs", Language.RUST);
        languages.put(".c", Language.C);
        languages.put(".h", Language.C);
        languages.put(".cpp", Language.CPP);
        languages.put(".cc", Language.CPP);
        languages.put(".cxx", Language.CPP);
        languages.put(".hpp", Language.CPP);
        languages.put(".cs", Language.CSHARP);
        languages.put(".kt", Language.KOTLIN);
        languages.put(".kts", Language.KOTLIN);
        languages.put(".swift", Language.SWIFT);
        languages.put(".yaml", Language.YAML);
        languages.put(".yml", Language.YAML);
        languages.put(".json", Language.JSON);
        languages.put(".sql", Language.SQL);
        languages.put(".sh", Language.SHELL);
        languages.put(".bash", Language.SHELL);
        languages.put(".zsh", Language.SHELL);
        languages.put(".md", Language.MARKDOWN);
        languages.put(".markdown", Language.MARKDOWN);
        return Map.copyOf(languages);
    }

    private static final class UnsafeSymbolicLinkException extends IOException {
        private UnsafeSymbolicLinkException() {
            super("Repository contains an unsafe symbolic link");
        }
    }
}
