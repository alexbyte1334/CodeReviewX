package com.codereviewx.backend.rag.controller;

import com.codereviewx.backend.common.ApiResponse;
import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.dto.RepositoryIndexResponse;
import com.codereviewx.backend.rag.dto.RepositoryIndexStatusResponse;
import com.codereviewx.backend.rag.persistence.RagIndexJobStore;
import com.codereviewx.backend.rag.persistence.RagRepositoryStore;
import com.codereviewx.backend.rag.service.RagIndexJob;
import com.codereviewx.backend.rag.service.RagIndexService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.lang.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryIndexController {
    private final RagRepositoryStore repositories; private final RagIndexJobStore jobs; private final RagIndexService index;
    private final RagProperties properties;
    private final boolean enabled;
    public RepositoryIndexController(@Nullable RagRepositoryStore repositories, @Nullable RagIndexJobStore jobs, @Nullable RagIndexService index) { this(repositories,jobs,index,new RagProperties(),true); }
    public RepositoryIndexController(@Nullable RagRepositoryStore repositories, @Nullable RagIndexJobStore jobs, @Nullable RagIndexService index, boolean enabled) { this(repositories,jobs,index,new RagProperties(),enabled); }
    @Autowired
    public RepositoryIndexController(@Nullable RagRepositoryStore repositories, @Nullable RagIndexJobStore jobs, @Nullable RagIndexService index, RagProperties properties, @Value("${codereviewx.rag.enabled:false}") boolean enabled) { this.repositories= repositories; this.jobs=jobs; this.index=index; this.properties=properties; this.enabled=enabled; }
    public record Request(@NotBlank @Size(max=1000) @Pattern(regexp="https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?") String repoUrl,
                          @NotBlank @Size(max=255) @Pattern(regexp="[A-Za-z0-9_.-]+") String ref) {}
    @PostMapping("/index") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RepositoryIndexResponse> index(@Valid @RequestBody Request request) {
        if (!enabled) throw new RagDisabledException();
        URI uri=URI.create(request.repoUrl()); String[] parts=uri.getPath().replaceFirst("^/","").split("/");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid repository URL");
        String owner=parts[0], name=parts[1].replaceFirst("\\.git$", "");
        var repository = repositories.find("github",owner,name);
        boolean commitRef = request.ref().matches("[0-9a-f]{40}");
        var latest = repository.flatMap(r -> commitRef
                ? jobs.findLatest(r.id(), request.ref())
                : jobs.findLatest(r.id(), request.ref(), properties.getEmbeddingModel(),
                        properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION));
        var active = repository.flatMap(r -> jobs.findActive(r.id(), request.ref(), properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION));
        var ready = commitRef
                ? repository.flatMap(r -> jobs.findReadySnapshot(r.id(), request.ref(), properties.getEmbeddingModel(),
                        properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION))
                : latest.flatMap(this::compatibleReadySnapshot);
        if (ready.isPresent() && active.isEmpty()) {
            return ApiResponse.success(new RepositoryIndexResponse(ready.get().id(), "READY", owner+"/"+name, request.ref()));
        }
        var repo= repositories.ensure("github",owner,name,"https://github.com/"+owner+"/"+name+".git",request.ref(),properties.getEmbeddingModel(),properties.getEmbeddingDimensions(),RagProperties.INDEX_VERSION);
        var activeResult=jobs.createOrGetActive(repo.id(),request.ref(),"API",properties.getEmbeddingModel(),properties.getEmbeddingDimensions(),RagProperties.INDEX_VERSION);
        if (!activeResult.created()) throw new RagConflictException("Active index exists");
        return ApiResponse.success(new RepositoryIndexResponse(activeResult.jobId(),"QUEUED",owner+"/"+name,request.ref()));
    }
    @GetMapping("/{owner}/{repo}/index-status")
    public ApiResponse<RepositoryIndexStatusResponse> status(@PathVariable @Pattern(regexp="[A-Za-z0-9_.-]+") String owner,@PathVariable @Pattern(regexp="[A-Za-z0-9_.-]+") String repo,@RequestParam(required=false) String commitSha,@RequestParam(required=false) String ref) {
        if (!enabled) throw new RagDisabledException();
        if ((commitSha == null || commitSha.isBlank()) && (ref == null || ref.isBlank())) throw new IllegalArgumentException("commitSha or ref is required");
        if (commitSha != null && !commitSha.isBlank() && !commitSha.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("Invalid commitSha");
        if (ref != null && !ref.isBlank() && !ref.matches("[A-Za-z0-9_.-]{1,255}")) throw new IllegalArgumentException("Invalid ref");
        var record=repositories.find("github",owner,repo.replaceFirst("\\.git$", "")).orElseThrow(()->new RagNotFoundException("Repository not found"));
        String requestedRef = commitSha == null || commitSha.isBlank() ? ref : commitSha;
        RagIndexJob active = jobs.findActive(record.id(), requestedRef, properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION).orElse(null);
        if (active != null) {
            return ApiResponse.success(statusResponse(active));
        }
        if (commitSha == null || commitSha.isBlank()) {
            RagIndexJob value = jobs.findLatest(record.id(), ref, properties.getEmbeddingModel(),
                    properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION).orElse(null);
            if (value != null && isActive(value)) value = null;
            if (value != null && value.status() == RagIndexJob.Status.READY) {
                value = compatibleReadySnapshot(value).orElse(null);
            }
            if (value == null) return ApiResponse.success(new RepositoryIndexStatusResponse("NOT_INDEXED", null, null, null, null));
            return ApiResponse.success(statusResponse(value));
        }
        RagIndexJob latest = jobs.findLatest(record.id(), commitSha, properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION).orElse(null);
        if (latest != null && isActive(latest)) {
            return ApiResponse.success(statusResponse(latest));
        }
        RagIndexJob value = jobs.findReadySnapshot(record.id(), commitSha, properties.getEmbeddingModel(),
                        properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION).orElse(null);
        if (value == null && latest != null && latest.status() != RagIndexJob.Status.READY) {
            value = latest;
        }
        if (value == null) return ApiResponse.success(new RepositoryIndexStatusResponse("NOT_INDEXED", null, null, null, null));
        return ApiResponse.success(statusResponse(value));
    }

    private Optional<RagIndexJob> compatibleReadySnapshot(RagIndexJob job) {
        if (job.status() != RagIndexJob.Status.READY || job.resolvedCommitSha() == null) {
            return Optional.empty();
        }
        return jobs.findReadySnapshot(job.repositoryId(), job.resolvedCommitSha(), properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION);
    }

    private static RepositoryIndexStatusResponse statusResponse(RagIndexJob value) {
        return new RepositoryIndexStatusResponse(value.status().name(), value.resolvedCommitSha(), value.indexedChunkCount(),
                value.errorCode(), safeErrorMessage(value.errorCode()), value.phase(), value.indexedFileCount(),
                value.totalFileCount(), value.lastProgressAt(), value.deadlineAt());
    }

    private static boolean isActive(RagIndexJob job) {
        return job.status() == RagIndexJob.Status.QUEUED || job.status() == RagIndexJob.Status.RUNNING;
    }

    private static String safeErrorMessage(String code) {
        if (code == null) return null;
        return switch (code) {
            case "SHALLOW_CLONE_UNAVAILABLE", "REPOSITORY_NOT_FOUND", "EMBEDDING_UNAVAILABLE", "INDEX_LIMIT_EXCEEDED",
                    "CHECKOUT_FAILED", "CHUNKING_FAILED" -> code.replace('_', ' ').toLowerCase();
            default -> "Indexing failed";
        };
    }
    @PostMapping("/{owner}/{repo}/reindex") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RepositoryIndexResponse> reindex(
            @PathVariable @Pattern(regexp="[A-Za-z0-9_.-]+") String owner,
            @PathVariable @Pattern(regexp="[A-Za-z0-9_.-]+") String repo,
            @RequestParam(defaultValue="main") @NotBlank @Size(max=255) @Pattern(regexp="[A-Za-z0-9_.-]+") String ref) {
        if (!enabled) throw new RagDisabledException();
        var record=repositories.find("github",owner,repo.replaceFirst("\\.git$", "")).orElseThrow(()->new RagNotFoundException("Repository not found"));
        var active=jobs.createOrGetActive(record.id(),ref,"API",properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions(),RagProperties.INDEX_VERSION);
        if (!active.created()) throw new RagConflictException("Active index exists");
        return ApiResponse.success(new RepositoryIndexResponse(active.jobId(),"QUEUED",owner+"/"+repo.replaceFirst("\\.git$", ""),ref));
    }
}
