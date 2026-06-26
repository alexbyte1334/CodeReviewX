package com.codereviewx.backend.review.persistence.repository;

import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewTaskRepository extends JpaRepository<ReviewTaskEntity, Long> {

    List<ReviewTaskEntity> findAllByOrderByCreatedAtDesc();
}
