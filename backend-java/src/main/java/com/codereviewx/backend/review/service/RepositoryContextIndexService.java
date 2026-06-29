package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.github.GithubPrDiffFile;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import com.codereviewx.backend.review.github.GithubPrMetadataClientException;
import com.codereviewx.backend.review.github.GithubProperties;
import com.codereviewx.backend.review.github.GithubRepositoryContentHttpClient;
import com.codereviewx.backend.review.github.GithubRepositoryFileContent;
import com.codereviewx.backend.review.github.GithubRepositoryFileContentHttpResponse;
import com.codereviewx.backend.review.github.GithubRepositoryRef;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RepositoryContextIndexService {

    public static final String TOOL_NAME = "repository.context.index";

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".java", ".kt", ".py", ".js", ".jsx", ".ts", ".tsx", ".json", ".yml", ".yaml",
            ".xml", ".md", ".sql", ".properties", ".gradle", ".mjs", ".cjs"
    );

    private final GithubProperties properties;
    private final GithubRepositoryContentHttpClient contentHttpClient;

    public RepositoryContextIndexService(GithubProperties properties,
                                         GithubRepositoryContentHttpClient contentHttpClient) {
        this.properties = properties;
        this.contentHttpClient = contentHttpClient;
    }

    public RepositoryContextIndexResult index(GithubPrMetadata metadata, GithubPrDiff diff) {
        if (metadata == null || diff == null || diff.files() == null || !properties.hasToken()) {
            return RepositoryContextIndexResult.empty();
        }

        int maxFiles = Math.max(1, properties.getMaxContextFiles());
        int perFileMaxBytes = Math.max(1, properties.getPerFileContextMaxBytes());
        int maxContextBytes = Math.max(1, properties.getMaxContextBytes());
        GithubRepositoryRef repository = new GithubRepositoryRef(metadata.owner(), metadata.repo());
        List<RepositoryContextFile> files = new ArrayList<>();

        for (GithubPrDiffFile diffFile : diff.files()) {
            if (files.size() >= maxFiles) {
                break;
            }
            if (!shouldIndex(diffFile)) {
                continue;
            }
            try {
                GithubRepositoryFileContentHttpResponse response = contentHttpClient.fetchFileContent(
                        properties.getApiBaseUrl(),
                        repository,
                        diffFile.filename(),
                        metadata.headSha(),
                        properties.getToken(),
                        properties.getTimeoutSeconds(),
                        perFileMaxBytes
                );
                GithubRepositoryFileContent content = response.content();
                if (content == null || content.content() == null || content.content().isBlank()) {
                    continue;
                }
                files.add(new RepositoryContextFile(
                        content.path(),
                        languageForPath(content.path()),
                        content.sizeBytes(),
                        content.truncated(),
                        content.content()
                ));
            } catch (GithubPrMetadataClientException ex) {
                // Repository context is additive. GitHub diff review should continue if one file cannot be fetched.
            }
        }

        if (files.isEmpty()) {
            return RepositoryContextIndexResult.empty();
        }
        return buildContext(files, maxContextBytes);
    }

    private RepositoryContextIndexResult buildContext(List<RepositoryContextFile> files, int maxContextBytes) {
        StringBuilder builder = new StringBuilder();
        boolean truncated = false;
        builder.append("--- REPOSITORY CONTEXT INDEX START ---\n");
        builder.append("retrieval: changed files at PR head commit\n");
        builder.append("fileCount: ").append(files.size()).append('\n');

        for (RepositoryContextFile file : files) {
            String block = "\n[FILE] " + file.path()
                    + " language=" + file.language()
                    + " sizeBytes=" + file.sizeBytes()
                    + " truncated=" + file.truncated()
                    + "\n"
                    + file.content()
                    + "\n[/FILE]\n";
            if (byteLength(builder.toString()) + byteLength(block) > maxContextBytes) {
                truncated = true;
                break;
            }
            builder.append(block);
            truncated = truncated || Boolean.TRUE.equals(file.truncated());
        }
        builder.append("--- REPOSITORY CONTEXT INDEX END ---\n");

        return new RepositoryContextIndexResult(
                files,
                files.size(),
                byteLength(builder.toString()),
                truncated,
                builder.toString()
        );
    }

    private static boolean shouldIndex(GithubPrDiffFile file) {
        if (file == null || file.filename() == null || file.filename().isBlank()) {
            return false;
        }
        if ("removed".equalsIgnoreCase(file.status())) {
            return false;
        }
        String filename = file.filename().toLowerCase(Locale.ROOT);
        if (filename.endsWith("package.json") || filename.endsWith("pom.xml")) {
            return true;
        }
        return TEXT_EXTENSIONS.stream().anyMatch(filename::endsWith);
    }

    private static String languageForPath(String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".java")) {
            return "java";
        }
        if (normalized.endsWith(".ts") || normalized.endsWith(".tsx")) {
            return "typescript";
        }
        if (normalized.endsWith(".js") || normalized.endsWith(".jsx") || normalized.endsWith(".mjs")) {
            return "javascript";
        }
        if (normalized.endsWith(".py")) {
            return "python";
        }
        if (normalized.endsWith(".json")) {
            return "json";
        }
        if (normalized.endsWith(".xml") || normalized.endsWith("pom.xml")) {
            return "xml";
        }
        if (normalized.endsWith(".yml") || normalized.endsWith(".yaml")) {
            return "yaml";
        }
        return "text";
    }

    private static int byteLength(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }
}
