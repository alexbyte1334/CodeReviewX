package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.persistence.entity.ReviewProviderTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewProviderTraceRepository extends JpaRepository<ReviewProviderTraceEntity, Long> {

    Optional<ReviewProviderTraceEntity> findByReviewRunId(Long reviewRunId);

    List<ReviewProviderTraceEntity> findByReviewRunIdIn(Collection<Long> reviewRunIds);
}
