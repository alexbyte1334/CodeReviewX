package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewCommentPreviewRepository extends JpaRepository<ReviewCommentPreviewEntity, Long> {

    List<ReviewCommentPreviewEntity> findByReviewRunIdOrderByIdAsc(Long reviewRunId);

    int countByReviewRunId(Long reviewRunId);
}
