package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;

public interface ReviewTraceObserver {
    void onTraceRecorded(ReviewToolTraceEntity trace);
}
