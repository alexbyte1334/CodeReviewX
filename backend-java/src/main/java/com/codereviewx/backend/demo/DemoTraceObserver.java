package com.codereviewx.backend.demo;

import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import com.codereviewx.backend.review.service.ReviewTraceObserver;
import org.springframework.stereotype.Component;

@Component
public class DemoTraceObserver implements ReviewTraceObserver {
    private final DemoStore store;

    public DemoTraceObserver(DemoStore store) {
        this.store = store;
    }

    @Override
    public void onTraceRecorded(ReviewToolTraceEntity trace) {
        store.appendTrace(trace);
    }
}
