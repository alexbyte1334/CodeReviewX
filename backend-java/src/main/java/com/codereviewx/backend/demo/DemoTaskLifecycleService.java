package com.codereviewx.backend.demo;

import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewRunStatus;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.persistence.entity.ReviewRunEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewRunRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DemoTaskLifecycleService {
    private final ReviewTaskRepository tasks;
    private final ReviewRunRepository runs;

    public DemoTaskLifecycleService(ReviewTaskRepository tasks, ReviewRunRepository runs) {
        this.tasks = tasks;
        this.runs = runs;
    }

    @Transactional
    public CreatedTask create(String repoUrl, int prNumber) {
        LocalDateTime now = LocalDateTime.now();
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setRepoUrl(repoUrl);
        task.setPrNumber(prNumber);
        task.setReviewMode(ReviewMode.GITHUB_PR);
        task.setStatus(ReviewTaskStatus.PENDING);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task = tasks.save(task);

        ReviewRunEntity run = new ReviewRunEntity();
        run.setReviewTaskId(task.getId());
        run.setRunNumber(1);
        run.setReviewMode(ReviewMode.GITHUB_PR);
        run.setStatus(ReviewRunStatus.PENDING);
        run.setStartedAt(now);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        run = runs.save(run);

        task.setLatestRunId(run.getId());
        task.setUpdatedAt(now);
        tasks.save(task);
        return new CreatedTask(task.getId(), run.getId());
    }

    public record CreatedTask(long taskId, long runId) {}
}
