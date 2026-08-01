package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.persistence.entity.ReviewApiRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewApiRunRepository extends JpaRepository<ReviewApiRunEntity, Long> {
    Optional<ReviewApiRunEntity> findByPublicId(String publicId);
    Optional<ReviewApiRunEntity> findByIdempotencyKey(String idempotencyKey);
    List<ReviewApiRunEntity> findAllByOrderByCreatedAtDesc();
}
