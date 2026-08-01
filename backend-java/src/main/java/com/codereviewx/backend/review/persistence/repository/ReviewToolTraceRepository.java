package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewToolTraceRepository extends JpaRepository<ReviewToolTraceEntity, Long> {

    List<ReviewToolTraceEntity> findByReviewApiRunIdOrderBySequenceNumberAsc(Long reviewApiRunId);

    int countByReviewApiRunId(Long reviewApiRunId);

    int countByReviewApiRunIdAndStatus(Long reviewApiRunId, ToolTraceStatus status);

    List<ReviewToolTraceEntity> findByReviewApiRunIdAndToolName(Long reviewApiRunId, String toolName);

    @Query("""
            select trace.reviewApiRunId as reviewApiRunId, count(trace) as itemCount
            from ReviewToolTraceEntity trace
            where trace.reviewApiRunId in :reviewApiRunIds
            group by trace.reviewApiRunId
            """)
    List<RunCountProjection> countByReviewApiRunIds(@Param("reviewApiRunIds") Collection<Long> reviewApiRunIds);

    @Query("""
            select trace.reviewApiRunId as reviewApiRunId, count(trace) as itemCount
            from ReviewToolTraceEntity trace
            where trace.reviewApiRunId in :reviewApiRunIds
              and trace.status = :status
            group by trace.reviewApiRunId
            """)
    List<RunCountProjection> countByReviewApiRunIdsAndStatus(
            @Param("reviewApiRunIds") Collection<Long> reviewApiRunIds,
            @Param("status") ToolTraceStatus status);
}
