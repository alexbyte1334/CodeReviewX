package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewIssueRepository extends JpaRepository<ReviewIssueEntity, Long> {

    List<ReviewIssueEntity> findByReviewTaskIdOrderByIdAsc(Long reviewTaskId);
}
