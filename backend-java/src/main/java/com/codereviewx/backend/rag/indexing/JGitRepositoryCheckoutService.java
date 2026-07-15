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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
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
        Git git = null;
        try {
            createOwnerOnlyDirectory(workRoot);
            checkout = Files.createTempDirectory(workRoot, "checkout-");
            setOwnerOnly(checkout);
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
            lastCheckout = new CheckedOutRepository(checkout, head.name(), git.getRepository());
            git = null;
            return lastCheckout;
        } catch (Exception exception) {
            if (git != null) {
                git.close();
            }
            if (checkout != null) {
                new CheckedOutRepository(checkout, metadata.headSha(), null).close();
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

    private static void createOwnerOnlyDirectory(Path directory) throws Exception {
        Files.createDirectories(directory);
        setOwnerOnly(directory);
    }

    private static void setOwnerOnly(Path directory) throws Exception {
        try {
            Files.setPosixFilePermissions(directory, OWNER_ONLY);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems do not expose mode bits.
        }
    }

    void closeLastCheckoutForTesting() {
        if (lastCheckout != null) {
            lastCheckout.close();
        }
    }
}
