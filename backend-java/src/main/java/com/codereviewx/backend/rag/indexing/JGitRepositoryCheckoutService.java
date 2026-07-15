package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.model.CheckedOutRepository;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import com.codereviewx.backend.review.github.GithubProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public final class JGitRepositoryCheckoutService implements RepositoryCheckoutService {

    private static final Pattern REPOSITORY_PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-f]{40}");
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);

    private final Path workRoot;
    private final int depth;
    private final String token;
    private final String localTestUri;
    private CheckedOutRepository lastCheckout;

    @Autowired
    public JGitRepositoryCheckoutService(RagProperties ragProperties, GithubProperties githubProperties) {
        this(ragProperties.getWorkRoot(), ragProperties.getFetchDepth(), githubProperties.getToken(), null);
    }

    private JGitRepositoryCheckoutService(Path workRoot, int depth, String token, String localTestUri) {
        this.workRoot = workRoot.toAbsolutePath().normalize();
        this.depth = depth;
        this.token = token == null ? "" : token;
        this.localTestUri = localTestUri;
    }

    static JGitRepositoryCheckoutService forLocalTesting(Path workRoot, int depth, String uri) {
        return new JGitRepositoryCheckoutService(workRoot, depth, "", uri);
    }

    static JGitRepositoryCheckoutService forLocalTesting(Path workRoot, int depth, String uri, String token) {
        return new JGitRepositoryCheckoutService(workRoot, depth, token, uri);
    }

    @Override
    public CheckedOutRepository checkout(GithubPrMetadata metadata) {
        validateMetadata(metadata);
        Path checkout = null;
        Path validatedRoot = null;
        Git git = null;
        try {
            validatedRoot = validateAndCreateRoot();
            checkout = createWorkspace(validatedRoot);
            String uri = localTestUri == null
                    ? canonicalGithubUri(metadata.owner(), metadata.repo())
                    : localTestUri;
            var clone = Git.cloneRepository()
                    .setURI(uri)
                    .setDirectory(checkout.toFile())
                    .setNoCheckout(true)
                    .setDepth(depth)
                    .setCloneAllBranches(true);
            CredentialsProvider credentials = credentials();
            if (credentials != null) {
                clone.setCredentialsProvider(credentials);
            }
            git = clone.call();
            checkoutExactCommit(git, metadata.headSha(), credentials);
            ObjectId head = git.getRepository().resolve(Constants.HEAD);
            if (head == null || !head.name().equalsIgnoreCase(metadata.headSha())) {
                throw new IllegalStateException("Unexpected repository revision");
            }
            Path leasedRoot = validatedRoot;
            Path leasedWorkspace = checkout;
            lastCheckout = new CheckedOutRepository(checkout, head.name(), git.getRepository(),
                    () -> cleanupWorkspace(leasedRoot, leasedWorkspace));
            git = null;
            return lastCheckout;
        } catch (Exception exception) {
            if (git != null) {
                git.close();
            }
            if (checkout != null && validatedRoot != null) {
                try {
                    cleanupWorkspace(validatedRoot, checkout);
                } catch (Exception ignored) {
                }
            }
            throw new IllegalStateException("Repository checkout failed");
        }
    }

    private void checkoutExactCommit(Git git, String sha, CredentialsProvider credentials) throws Exception {
        ObjectId commit = ObjectId.fromString(sha);
        if (!git.getRepository().getObjectDatabase().has(commit)) {
            var fetch = git.fetch()
                    .setDepth(1)
                    .setRefSpecs(new RefSpec("+" + sha + ":refs/codereviewx/commit"));
            if (credentials != null) {
                fetch.setCredentialsProvider(credentials);
            }
            fetch.call();
        }
        RefUpdate head = git.getRepository().updateRef(Constants.HEAD, true);
        head.setNewObjectId(commit);
        head.setForceUpdate(true);
        RefUpdate.Result result = head.update();
        if (result != RefUpdate.Result.FORCED && result != RefUpdate.Result.NEW
                && result != RefUpdate.Result.FAST_FORWARD && result != RefUpdate.Result.NO_CHANGE) {
            throw new IllegalStateException("Repository revision could not be selected");
        }
        git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).setRef(sha).call();
    }

    private CredentialsProvider credentials() {
        return token.isBlank() ? null : new UsernamePasswordCredentialsProvider("x-access-token", token);
    }

    static String canonicalGithubUri(String owner, String repo) {
        validatePart(owner);
        validatePart(repo);
        return "https://github.com/" + owner + "/" + repo + ".git";
    }

    static String validateGithubUri(String rawUri) {
        try {
            URI uri = URI.create(rawUri);
            if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost()) || uri.getPort() != -1
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getRawPath() == null || uri.getRawPath().contains("%") || uri.getRawPath().contains("//")) {
                throw new IllegalArgumentException();
            }
            String path = uri.getRawPath();
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String[] parts = path.split("/", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException();
            }
            String repo = parts[2].endsWith(".git") ? parts[2].substring(0, parts[2].length() - 4) : parts[2];
            return canonicalGithubUri(parts[1], repo);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Repository URL is not allowed");
        }
    }

    private static void validateMetadata(GithubPrMetadata metadata) {
        if (metadata == null || !COMMIT_SHA.matcher(metadata.headSha() == null ? "" : metadata.headSha()).matches()) {
            throw new IllegalArgumentException("Repository metadata is invalid");
        }
        canonicalGithubUri(metadata.owner(), metadata.repo());
    }

    private static void validatePart(String value) {
        if (value == null || value.equals(".") || value.equals("..") || !REPOSITORY_PART.matcher(value).matches()) {
            throw new IllegalArgumentException("Repository URL is not allowed");
        }
    }

    private Path validateAndCreateRoot() throws Exception {
        Path existing = workRoot;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null || Files.isSymbolicLink(existing) || !existing.toRealPath().equals(existing)) {
            throw new IllegalStateException("Repository work root is unsafe");
        }
        Files.createDirectories(workRoot);
        if (Files.isSymbolicLink(workRoot)) {
            throw new IllegalStateException("Repository work root is unsafe");
        }
        Path realRoot = workRoot.toRealPath();
        if (!realRoot.equals(workRoot)) {
            throw new IllegalStateException("Repository work root is unsafe");
        }
        setOwnerOnly(realRoot);
        return realRoot;
    }

    private static Path createWorkspace(Path root) throws Exception {
        Path workspace;
        FileAttribute<Set<PosixFilePermission>> permissions =
                PosixFilePermissions.asFileAttribute(OWNER_ONLY);
        try {
            workspace = Files.createTempDirectory(root, "checkout-", permissions);
        } catch (UnsupportedOperationException exception) {
            workspace = Files.createTempDirectory(root, "checkout-");
        }
        setOwnerOnly(workspace);
        Path normalized = workspace.toAbsolutePath().normalize();
        if (!normalized.getParent().equals(root) || Files.isSymbolicLink(normalized)
                || !normalized.toRealPath().equals(normalized)) {
            throw new IllegalStateException("Repository workspace is unsafe");
        }
        return normalized;
    }

    private static void setOwnerOnly(Path directory) throws Exception {
        PosixFileAttributeView view = Files.getFileAttributeView(directory, PosixFileAttributeView.class);
        if (view != null) {
            Files.setPosixFilePermissions(directory, OWNER_ONLY);
            if (!Files.getPosixFilePermissions(directory).equals(OWNER_ONLY)) {
                throw new IllegalStateException("Repository workspace permissions are unsafe");
            }
        }
    }

    private void cleanupWorkspace(Path validatedRoot, Path workspace) throws IOException {
        if (!workRoot.equals(validatedRoot) || Files.isSymbolicLink(workRoot)
                || !workRoot.toRealPath().equals(validatedRoot)) {
            throw new IOException("Unsafe repository cleanup boundary");
        }
        if (!Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!workspace.toAbsolutePath().normalize().getParent().equals(validatedRoot)
                || Files.isSymbolicLink(workspace)
                || !workspace.toRealPath().equals(workspace.toAbsolutePath().normalize())) {
            throw new IOException("Unsafe repository cleanup boundary");
        }
        Files.walkFileTree(workspace, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    void closeLastCheckoutForTesting() {
        if (lastCheckout != null) {
            lastCheckout.close();
        }
    }
}
