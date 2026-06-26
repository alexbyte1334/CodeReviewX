package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewToolTraceRepository extends JpaRepository<ReviewToolTraceEntity, Long> {

    List<ReviewToolTraceEntity> findByReviewRunIdOrderBySequenceNumberAsc(Long reviewRunId);

    int countByReviewRunId(Long reviewRunId);

    int countByReviewRunIdAndStatus(Long reviewRunId, com.codereviewx.backend.review.enums.ToolTraceStatus status);
}
