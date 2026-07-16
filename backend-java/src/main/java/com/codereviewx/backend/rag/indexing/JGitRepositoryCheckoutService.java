package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.config.RagProperties;
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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public final class JGitRepositoryCheckoutService implements RepositoryCheckoutService {

    private static final Pattern REPOSITORY_PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-f]{40}");
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final WorkspaceBoundaryHook NOOP_HOOK = (root, operation) -> { };

    private final Path workRoot;
    private final int depth;
    private final String token;
    private final String localTestUri;
    private final WorkspaceBoundaryHook boundaryHook;
    private CheckedOutRepository lastCheckout;

    @Autowired
    public JGitRepositoryCheckoutService(RagProperties ragProperties, GithubProperties githubProperties) {
        this(ragProperties.getWorkRoot(), ragProperties.getFetchDepth(), githubProperties.getToken(), null, NOOP_HOOK);
    }

    private JGitRepositoryCheckoutService(Path workRoot, int depth, String token, String localTestUri,
                                          WorkspaceBoundaryHook boundaryHook) {
        this.workRoot = workRoot.toAbsolutePath().normalize();
        this.depth = depth;
        this.token = token == null ? "" : token;
        this.localTestUri = localTestUri;
        this.boundaryHook = boundaryHook;
    }

    static JGitRepositoryCheckoutService forLocalTesting(Path workRoot, int depth, String uri) {
        return new JGitRepositoryCheckoutService(workRoot, depth, "", uri, NOOP_HOOK);
    }

    static JGitRepositoryCheckoutService forLocalTesting(Path workRoot, int depth, String uri, String token) {
        return new JGitRepositoryCheckoutService(workRoot, depth, token, uri, NOOP_HOOK);
    }

    static JGitRepositoryCheckoutService forLocalTesting(Path workRoot, int depth, String uri,
                                                          WorkspaceBoundaryHook boundaryHook) {
        return new JGitRepositoryCheckoutService(workRoot, depth, "", uri, boundaryHook);
    }

    @Override
    public CheckedOutRepository checkout(GithubPrMetadata metadata) {
        validateMetadata(metadata);
        WorkspaceLease workspaceLease = null;
        Git git = null;
        try {
            workspaceLease = createWorkspaceLease();
            Path checkout = workspaceLease.workspace();
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
            WorkspaceLease managedLease = workspaceLease;
            lastCheckout = new CheckedOutRepository(checkout, head.name(), git.getRepository(),
                    () -> cleanupWorkspace(managedLease));
            git = null;
            return lastCheckout;
        } catch (Exception exception) {
            if (git != null) {
                git.close();
            }
            if (workspaceLease != null) {
                try {
                    cleanupWorkspace(workspaceLease);
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
        requireOwnerOnly(realRoot);
        return realRoot;
    }

    private WorkspaceLease createWorkspaceLease() throws Exception {
        Path root = validateAndCreateRoot();
        Object rootFileKey = requireFileKey(readAttributes(root));
        try (SecureDirectoryStream<Path> secureRoot = openSecureDirectory(root)) {
            boundaryHook.run(root, BoundaryOperation.CREATE);
            verifyRootIdentity(root, rootFileKey);
            return createWorkspaceInSecureRoot(root, rootFileKey, secureRoot);
        }
    }

    private WorkspaceLease createWorkspaceInSecureRoot(Path root, Object rootFileKey,
                                                       SecureDirectoryStream<Path> secureRoot) throws Exception {
        Path workspace = null;
        Path workspaceName = null;
        FileAttribute<Set<PosixFilePermission>> permissions =
                PosixFilePermissions.asFileAttribute(OWNER_ONLY);
        try {
            workspace = Files.createTempDirectory(root, "checkout-", permissions);
            workspaceName = workspace.getFileName();
            verifyRootIdentity(root, rootFileKey);
            requireOwnerOnly(workspace);
            Path normalized = workspace.toAbsolutePath().normalize();
            BasicFileAttributes attributes = readRelativeAttributes(secureRoot, workspaceName);
            Object workspaceFileKey = requireFileKey(attributes);
            if (!normalized.getParent().equals(root) || attributes.isSymbolicLink() || !attributes.isDirectory()
                    || !normalized.toRealPath().equals(normalized)) {
                throw new IllegalStateException("Repository workspace is unsafe");
            }
            return new WorkspaceLease(root, normalized, workspaceName, rootFileKey, workspaceFileKey);
        } catch (Exception exception) {
            if (workspaceName != null) {
                deleteRelativeEntryIfPresent(secureRoot, workspaceName);
            }
            throw exception;
        }
    }

    static void requireOwnerOnly(Path directory) {
        PosixFileAttributeView view = Files.getFileAttributeView(directory, PosixFileAttributeView.class);
        if (view == null) {
            throw new IllegalStateException("Repository workspace permissions are unsafe");
        }
        try {
            Files.setPosixFilePermissions(directory, OWNER_ONLY);
            if (!Files.getPosixFilePermissions(directory).equals(OWNER_ONLY)) {
                throw new IllegalStateException("Repository workspace permissions are unsafe");
            }
        } catch (UnsupportedOperationException | IOException exception) {
            throw new IllegalStateException("Repository workspace permissions are unsafe");
        }
    }

    private void cleanupWorkspace(WorkspaceLease lease) throws Exception {
        if (!workRoot.equals(lease.root())) {
            throw new IOException("Unsafe repository cleanup boundary");
        }
        verifyRootIdentity(lease.root(), lease.rootFileKey());
        try (SecureDirectoryStream<Path> secureRoot = openSecureDirectory(lease.root())) {
            verifyRootIdentity(lease.root(), lease.rootFileKey());
            boundaryHook.run(lease.root(), BoundaryOperation.CLEANUP);
            BasicFileAttributes workspaceAttributes;
            try {
                workspaceAttributes = readRelativeAttributes(secureRoot, lease.workspaceName());
            } catch (NoSuchFileException exception) {
                return;
            }
            if (!Objects.equals(workspaceAttributes.fileKey(), lease.workspaceFileKey())
                    || workspaceAttributes.isSymbolicLink() || !workspaceAttributes.isDirectory()) {
                throw new IOException("Unsafe repository cleanup boundary");
            }
            deleteRelativeDirectory(secureRoot, lease.workspaceName());
        }
    }

    @SuppressWarnings("unchecked")
    private static SecureDirectoryStream<Path> openSecureDirectory(Path directory) throws IOException {
        DirectoryStream<Path> stream = Files.newDirectoryStream(directory);
        if (!(stream instanceof SecureDirectoryStream<?>)) {
            stream.close();
            throw new IOException("Secure repository directory operations are unavailable");
        }
        return (SecureDirectoryStream<Path>) stream;
    }

    private static void verifyRootIdentity(Path root, Object expectedFileKey) throws IOException {
        BasicFileAttributes attributes = readAttributes(root);
        if (attributes.isSymbolicLink() || !attributes.isDirectory()
                || !Objects.equals(attributes.fileKey(), expectedFileKey)
                || !root.toRealPath().equals(root)) {
            throw new IOException("Unsafe repository root identity");
        }
    }

    private static BasicFileAttributes readAttributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static BasicFileAttributes readRelativeAttributes(SecureDirectoryStream<Path> directory,
                                                              Path name) throws IOException {
        BasicFileAttributeView view = directory.getFileAttributeView(
                name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IOException("Secure repository attributes are unavailable");
        }
        return view.readAttributes();
    }

    private static Object requireFileKey(BasicFileAttributes attributes) throws IOException {
        if (attributes.fileKey() == null) {
            throw new IOException("Repository filesystem identity is unavailable");
        }
        return attributes.fileKey();
    }

    private static void deleteRelativeEntryIfPresent(SecureDirectoryStream<Path> parent, Path name) throws IOException {
        try {
            BasicFileAttributes attributes = readRelativeAttributes(parent, name);
            if (attributes.isDirectory() && !attributes.isSymbolicLink()) {
                deleteRelativeDirectory(parent, name);
            } else {
                parent.deleteFile(name);
            }
        } catch (NoSuchFileException ignored) {
        }
    }

    private static void deleteRelativeDirectory(SecureDirectoryStream<Path> parent, Path name) throws IOException {
        try (SecureDirectoryStream<Path> child = parent.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
            List<Path> entries = new ArrayList<>();
            for (Path entry : child) {
                entries.add(entry.getFileName());
            }
            for (Path entryName : entries) {
                BasicFileAttributes attributes = readRelativeAttributes(child, entryName);
                if (attributes.isDirectory() && !attributes.isSymbolicLink()) {
                    deleteRelativeDirectory(child, entryName);
                } else {
                    child.deleteFile(entryName);
                }
            }
        }
        parent.deleteDirectory(name);
    }

    void closeLastCheckoutForTesting() {
        if (lastCheckout != null) {
            lastCheckout.close();
        }
    }

    enum BoundaryOperation {
        CREATE, CLEANUP
    }

    @FunctionalInterface
    interface WorkspaceBoundaryHook {
        void run(Path root, BoundaryOperation operation) throws Exception;
    }

    private record WorkspaceLease(
            Path root,
            Path workspace,
            Path workspaceName,
            Object rootFileKey,
            Object workspaceFileKey
    ) { }
}
