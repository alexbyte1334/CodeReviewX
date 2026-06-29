package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewCommentPreviewRepository extends JpaRepository<ReviewCommentPreviewEntity, Long> {

    List<ReviewCommentPreviewEntity> findByReviewRunIdOrderByIdAsc(Long reviewRunId);

    List<ReviewCommentPreviewEntity> findByReviewRunIdAndSelectedForPublishTrueOrderByIdAsc(Long reviewRunId);

    Optional<ReviewCommentPreviewEntity> findByIdAndReviewRunId(Long id, Long reviewRunId);

    int countByReviewRunId(Long reviewRunId);

    @Query("""
            select preview.reviewRunId as reviewRunId, count(preview) as itemCount
            from ReviewCommentPreviewEntity preview
            where preview.reviewRunId in :reviewRunIds
            group by preview.reviewRunId
            """)
    List<RunCountProjection> countByReviewRunIds(@Param("reviewRunIds") Collection<Long> reviewRunIds);
}
