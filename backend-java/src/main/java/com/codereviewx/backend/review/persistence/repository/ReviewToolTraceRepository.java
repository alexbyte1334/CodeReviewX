package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewToolTraceRepository extends JpaRepository<ReviewToolTraceEntity, Long> {

    List<ReviewToolTraceEntity> findByReviewRunIdOrderBySequenceNumberAsc(Long reviewRunId);

    int countByReviewRunId(Long reviewRunId);

    int countByReviewRunIdAndStatus(Long reviewRunId, ToolTraceStatus status);

    @Query("""
            select trace.reviewRunId as reviewRunId, count(trace) as itemCount
            from ReviewToolTraceEntity trace
            where trace.reviewRunId in :reviewRunIds
            group by trace.reviewRunId
            """)
    List<RunCountProjection> countByReviewRunIds(@Param("reviewRunIds") Collection<Long> reviewRunIds);

    @Query("""
            select trace.reviewRunId as reviewRunId, count(trace) as itemCount
            from ReviewToolTraceEntity trace
            where trace.reviewRunId in :reviewRunIds
              and trace.status = :status
            group by trace.reviewRunId
            """)
    List<RunCountProjection> countByReviewRunIdsAndStatus(
            @Param("reviewRunIds") Collection<Long> reviewRunIds,
            @Param("status") ToolTraceStatus status);
}
