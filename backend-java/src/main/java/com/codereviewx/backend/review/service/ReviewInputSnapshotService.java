package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.github.GithubPrDiffFile;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewInputSnapshotService {

    private final ReviewInputSnapshotRepository inputSnapshotRepository;
    private final ObjectMapper objectMapper;

    public ReviewInputSnapshotService(ReviewInputSnapshotRepository inputSnapshotRepository,
                                      ObjectMapper objectMapper) {
        this.inputSnapshotRepository = inputSnapshotRepository;
        this.objectMapper = objectMapper;
    }

    public void persistGithubPrSnapshot(Long runId,
                                        ReviewTaskEntity task,
                                        GithubPrMetadata metadata,
                                        GithubPrDiff diff,
                                        LocalDateTime now) {
        ReviewInputSnapshotEntity snapshot = new ReviewInputSnapshotEntity();
        snapshot.setReviewRunId(runId);
        snapshot.setRepoUrl(task.getRepoUrl());
        snapshot.setOwner(metadata.owner());
        snapshot.setRepo(metadata.repo());
        snapshot.setPrNumber(metadata.prNumber());
        snapshot.setBaseRef(metadata.baseRef());
        snapshot.setHeadRef(metadata.headRef());
        snapshot.setBaseSha(metadata.baseSha());
        snapshot.setHeadSha(metadata.headSha());
        snapshot.setPrTitle(metadata.title());
        snapshot.setPrAuthor(metadata.authorLogin());
        snapshot.setChangedFiles(metadata.changedFiles());
        snapshot.setAdditions(metadata.additions());
        snapshot.setDeletions(metadata.deletions());
        snapshot.setDiffTruncated(diff.diffTruncated());
        snapshot.setContextTruncated(false);
        snapshot.setSnapshotJson(toSnapshotJson(metadata, diff));
        snapshot.setCreatedAt(now);
        inputSnapshotRepository.save(snapshot);
    }

    private String toSnapshotJson(GithubPrMetadata metadata, GithubPrDiff diff) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("owner", metadata.owner());
        snapshot.put("repo", metadata.repo());
        snapshot.put("prNumber", metadata.prNumber());
        snapshot.put("title", metadata.title());
        snapshot.put("authorLogin", metadata.authorLogin());
        snapshot.put("baseRef", metadata.baseRef());
        snapshot.put("headRef", metadata.headRef());
        snapshot.put("baseSha", metadata.baseSha());
        snapshot.put("headSha", metadata.headSha());
        snapshot.put("state", metadata.state());
        snapshot.put("createdAt", metadata.createdAt());
        snapshot.put("updatedAt", metadata.updatedAt());
        snapshot.put("changedFiles", metadata.changedFiles());
        snapshot.put("additions", metadata.additions());
        snapshot.put("deletions", metadata.deletions());
        snapshot.put("diffFileCount", diff.fileCount());
        snapshot.put("diffBytes", diff.diffBytes());
        snapshot.put("diffTruncated", diff.diffTruncated());
        snapshot.put("diffFiles", diff.files().stream()
                .map(this::toSnapshotFileSummary)
                .collect(Collectors.toList()));
        snapshot.put("contextTruncated", false);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            return "{\"metadata\":\"sanitized\"}";
        }
    }

    private Map<String, Object> toSnapshotFileSummary(GithubPrDiffFile file) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("filename", file.filename());
        summary.put("status", file.status());
        summary.put("additions", file.additions());
        summary.put("deletions", file.deletions());
        summary.put("changes", file.changes());
        summary.put("patchBytes", file.patchBytes());
        summary.put("patchTruncated", file.patchTruncated());
        return summary;
    }
}
