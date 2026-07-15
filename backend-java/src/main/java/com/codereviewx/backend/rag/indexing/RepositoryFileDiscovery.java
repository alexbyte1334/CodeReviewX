package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.model.Language;
import com.codereviewx.backend.rag.model.RepositoryFile;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public final class RepositoryFileDiscovery {

    private static final long DEFAULT_MAX_FILE_BYTES = 1024L * 1024L;
    private static final int DEFAULT_MAX_FILES = 5000;
    private static final long DEFAULT_MAX_TEXT_BYTES = 100L * 1024L * 1024L;
    private static final int DEFAULT_MAX_SCANNED_ENTRIES = 50_000;
    private static final long DEFAULT_MAX_SCANNED_BYTES = 500L * 1024L * 1024L;
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", "node_modules", "dist", "build", "target", "vendor");
    private static final Set<String> SKIPPED_FILES = Set.of(
            ".env", "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "pnpm-lock.yml",
            "composer.lock", "cargo.lock", "gemfile.lock", "poetry.lock",
            ".npmrc", ".pypirc", ".netrc", ".dockercfg", ".git-credentials");
    private static final Set<String> SAFE_ENV_TEMPLATES = Set.of(
            ".env.example", ".env.sample", ".env.template");
    private static final Map<String, Language> LANGUAGES = languages();

    private final long maxFileBytes;
    private final int maxFiles;
    private final long maxTextBytes;
    private final int maxScannedEntries;
    private final long maxScannedBytes;

    public RepositoryFileDiscovery() {
        this(DEFAULT_MAX_FILE_BYTES, DEFAULT_MAX_FILES, DEFAULT_MAX_TEXT_BYTES,
                DEFAULT_MAX_SCANNED_ENTRIES, DEFAULT_MAX_SCANNED_BYTES);
    }

    @Autowired
    public RepositoryFileDiscovery(RagProperties properties) {
        this(properties.getMaxFileBytes(), properties.getMaxFiles(), properties.getMaxTextBytes(),
                properties.getMaxScannedEntries(), properties.getMaxScannedBytes());
    }

    RepositoryFileDiscovery(long maxFileBytes, int maxFiles, long maxTextBytes) {
        this(maxFileBytes, maxFiles, maxTextBytes, DEFAULT_MAX_SCANNED_ENTRIES, DEFAULT_MAX_SCANNED_BYTES);
    }

    RepositoryFileDiscovery(long maxFileBytes, int maxFiles, long maxTextBytes,
                            int maxScannedEntries, long maxScannedBytes) {
        if (maxFileBytes <= 0 || maxFiles <= 0 || maxTextBytes <= 0
                || maxScannedEntries <= 0 || maxScannedBytes <= 0) {
            throw new IllegalArgumentException("Repository discovery limits must be positive");
        }
        this.maxFileBytes = maxFileBytes;
        this.maxFiles = maxFiles;
        this.maxTextBytes = maxTextBytes;
        this.maxScannedEntries = maxScannedEntries;
        this.maxScannedBytes = maxScannedBytes;
    }

    public List<RepositoryFile> discover(CheckedOutRepository checkedOut) {
        Path root = checkedOut.path().toAbsolutePath().normalize();
        Repository repository = checkedOut.repository();
        boolean closeRepository = false;
        try {
            root.toRealPath();
            if (repository == null) {
                repository = new FileRepositoryBuilder().findGitDir(root.toFile()).build();
                closeRepository = true;
            }
            return discoverWithTreeWalk(root, repository);
        } catch (UnsafeSymbolicLinkException exception) {
            throw new IllegalStateException("Repository contains an unsafe symbolic link");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Repository file discovery failed");
        } finally {
            if (closeRepository && repository != null) {
                repository.close();
            }
        }
    }

    private List<RepositoryFile> discoverWithTreeWalk(Path root, Repository repository) throws Exception {
        List<RepositoryFile> files = new ArrayList<>();
        long totalBytes = 0;
        int scannedEntries = 0;
        long scannedBytes = 0;
        try (TreeWalk walk = new TreeWalk(repository)) {
            int indexPosition = walk.addTree(new DirCacheIterator(repository.readDirCache()));
            int worktreePosition = walk.addTree(new FileTreeIterator(repository));
            walk.setRecursive(false);
            while (walk.next()) {
                scannedEntries++;
                if (scannedEntries > maxScannedEntries) {
                    throw new IllegalStateException("Repository scan entry budget exceeded");
                }
                WorkingTreeIterator working = walk.getTree(worktreePosition, WorkingTreeIterator.class);
                if (working == null) {
                    continue;
                }
                String relative = walk.getPathString();
                FileMode mode = walk.getFileMode(worktreePosition);
                if (mode == FileMode.TREE) {
                    if (!SKIPPED_DIRECTORIES.contains(fileName(relative))) {
                        walk.enterSubtree();
                    }
                    continue;
                }
                if (mode == FileMode.SYMLINK) {
                    rejectEscapingSymlink(root, root.resolve(relative));
                    continue;
                }
                if (mode != FileMode.REGULAR_FILE && mode != FileMode.EXECUTABLE_FILE) {
                    continue;
                }
                Path candidate = root.resolve(relative);
                long size = Files.size(candidate);
                if (size > maxScannedBytes - scannedBytes) {
                    throw new IllegalStateException("Repository scan byte budget exceeded");
                }
                scannedBytes += size;
                boolean tracked = walk.getFileMode(indexPosition) != FileMode.MISSING;
                if ((!tracked && working.isEntryIgnored()) || shouldSkipFile(fileName(relative))) {
                    continue;
                }
                if (size > maxFileBytes) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(candidate);
                String content = decodeUtf8(bytes);
                if (content == null || containsBinaryControl(content)) {
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
        }
        return List.copyOf(files);
    }

    private static void rejectEscapingSymlink(Path root, Path link) throws IOException {
        Path linkTarget = Files.readSymbolicLink(link);
        Path target = (linkTarget.isAbsolute() ? linkTarget : link.getParent().resolve(linkTarget))
                .toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new UnsafeSymbolicLinkException();
        }
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static boolean shouldSkipFile(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return SKIPPED_FILES.contains(lower)
                || (lower.startsWith(".env.") && !SAFE_ENV_TEMPLATES.contains(lower))
                || lower.endsWith(".min.js")
                || lower.endsWith(".min.css")
                || lower.endsWith(".pem")
                || lower.endsWith(".key")
                || lower.endsWith(".p12")
                || lower.endsWith(".pfx")
                || lower.endsWith(".jks")
                || lower.endsWith(".keystore")
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

    private static boolean containsBinaryControl(String content) {
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (Character.isISOControl(character)
                    && character != '\t' && character != '\n' && character != '\r' && character != '\f') {
                return true;
            }
        }
        return false;
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
