package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewInputSnapshotRepository extends JpaRepository<ReviewInputSnapshotEntity, Long> {

    Optional<ReviewInputSnapshotEntity> findByReviewRunId(Long reviewRunId);
}
