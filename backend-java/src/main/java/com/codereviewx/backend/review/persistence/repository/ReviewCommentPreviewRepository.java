package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewCommentPreviewRepository extends JpaRepository<ReviewCommentPreviewEntity, Long> {

    List<ReviewCommentPreviewEntity> findByReviewApiRunIdOrderByIdAsc(Long reviewApiRunId);

    List<ReviewCommentPreviewEntity> findByReviewApiRunIdAndSelectedForPublishTrueOrderByIdAsc(Long reviewApiRunId);

    Optional<ReviewCommentPreviewEntity> findByIdAndReviewApiRunId(Long id, Long reviewApiRunId);

    int countByReviewApiRunId(Long reviewApiRunId);

    @Query("""
            select preview.reviewApiRunId as reviewApiRunId, count(preview) as itemCount
            from ReviewCommentPreviewEntity preview
            where preview.reviewApiRunId in :reviewApiRunIds
            group by preview.reviewApiRunId
            """)
    List<RunCountProjection> countByReviewApiRunIds(@Param("reviewApiRunIds") Collection<Long> reviewApiRunIds);
}
