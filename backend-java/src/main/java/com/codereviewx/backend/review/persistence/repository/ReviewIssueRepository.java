package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewIssueRepository extends JpaRepository<ReviewIssueEntity, Long> {

    List<ReviewIssueEntity> findByReviewTaskIdOrderByIdAsc(Long reviewTaskId);

    @Query("""
            select issue
            from ReviewIssueEntity issue
            where issue.reviewTask.id in :reviewTaskIds
            order by issue.reviewTask.id asc, issue.id asc
            """)
    List<ReviewIssueEntity> findAllByReviewTaskIdsOrderByTaskIdAndId(
            @Param("reviewTaskIds") Collection<Long> reviewTaskIds
    );
}
