package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.persistence.entity.ReviewRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRunRepository extends JpaRepository<ReviewRunEntity, Long> {

    Optional<ReviewRunEntity> findByReviewTaskIdAndRunNumber(Long reviewTaskId, Integer runNumber);
}
