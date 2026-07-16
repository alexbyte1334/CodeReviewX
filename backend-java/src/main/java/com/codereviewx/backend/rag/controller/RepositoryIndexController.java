package com.codereviewx.backend.rag.controller;

import com.codereviewx.backend.common.ApiResponse;
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

import java.net.URI;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryIndexController {
    private final RagRepositoryStore repositories; private final RagIndexJobStore jobs; private final RagIndexService index;
    private final boolean enabled;
    public RepositoryIndexController(@Nullable RagRepositoryStore repositories, @Nullable RagIndexJobStore jobs, @Nullable RagIndexService index) { this(repositories,jobs,index,true); }
    public RepositoryIndexController(RagRepositoryStore repositories, RagIndexJobStore jobs, RagIndexService index, boolean enabled) { this.repositories= repositories; this.jobs=jobs; this.index=index; this.enabled=enabled; }
    public record Request(@NotBlank @Size(max=1000) @Pattern(regexp="https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?") String repoUrl,
                          @NotBlank @Size(max=255) @Pattern(regexp="[A-Za-z0-9_.-]+") String ref) {}
    @PostMapping("/index") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RepositoryIndexResponse> index(@Valid @RequestBody Request request) {
        if (!enabled) throw new RagDisabledException();
        URI uri=URI.create(request.repoUrl()); String[] parts=uri.getPath().replaceFirst("^/","").split("/");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid repository URL");
        String owner=parts[0], name=parts[1].replaceFirst("\\.git$", "");
        if (repositories.find("github",owner,name).flatMap(r -> jobs.findLatest(r.id(),request.ref())).map(j -> j.status()== RagIndexJob.Status.QUEUED || j.status()== RagIndexJob.Status.RUNNING).orElse(false)) throw new RagConflictException("Active index exists");
        var repo= repositories.ensure("github",owner,name,"https://github.com/"+owner+"/"+name+".git",request.ref(),"",1024,1);
        long jobId=jobs.createOrGetActive(repo.id(),request.ref(),"API", "",1024,1);
        return ApiResponse.success(new RepositoryIndexResponse(jobId,"QUEUED",owner+"/"+name,request.ref()));
    }
    @GetMapping("/{owner}/{repo}/index-status")
    public ApiResponse<RepositoryIndexStatusResponse> status(@PathVariable @Pattern(regexp="[A-Za-z0-9_.-]+") String owner,@PathVariable @Pattern(regexp="[A-Za-z0-9_.-]+") String repo,@RequestParam @Pattern(regexp="[0-9a-f]{40}") String commitSha) {
        if (!enabled) throw new RagDisabledException();
        var record=repositories.find("github",owner,repo.replaceFirst("\\.git$", "")).orElseThrow(()->new RagNotFoundException("Repository not found"));
        RagIndexJob value = jobs.findReadySnapshot(record.id(), commitSha, record.embeddingModel(), record.embeddingDimensions(), record.indexVersion())
                .orElseGet(() -> jobs.findLatest(record.id(), commitSha).orElseGet(() -> jobs.findLatest(record.id(), record.defaultBranch()).orElse(null)));
        if (value == null) return ApiResponse.success(new RepositoryIndexStatusResponse("NOT_INDEXED", null, null, null, null));
        return ApiResponse.success(new RepositoryIndexStatusResponse(value.status().name(), value.resolvedCommitSha(), value.indexedChunkCount(),
                value.errorCode(), value.errorMessage()));
    }
    @PostMapping("/{owner}/{repo}/reindex") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RepositoryIndexResponse> reindex(
            @PathVariable @Pattern(regexp="[A-Za-z0-9_.-]+") String owner,
            @PathVariable @Pattern(regexp="[A-Za-z0-9_.-]+") String repo,
            @RequestParam(defaultValue="main") @NotBlank @Size(max=255) @Pattern(regexp="[A-Za-z0-9_.-]+") String ref) {
        if (!enabled) throw new RagDisabledException();
        var record=repositories.find("github",owner,repo.replaceFirst("\\.git$", "")).orElseThrow(()->new RagNotFoundException("Repository not found"));
        if (jobs.findLatest(record.id(),ref).map(j -> j.status()== RagIndexJob.Status.QUEUED || j.status()== RagIndexJob.Status.RUNNING).orElse(false)) throw new RagConflictException("Active index exists");
        long jobId=jobs.createOrGetActive(record.id(),ref,"API",record.embeddingModel(),record.embeddingDimensions(),record.indexVersion());
        return ApiResponse.success(new RepositoryIndexResponse(jobId,"QUEUED",owner+"/"+repo.replaceFirst("\\.git$", ""),ref));
    }
}
