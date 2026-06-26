package com.codereviewx.backend.review.enums;

public enum ReviewRunStatus {
    PENDING,
    INGESTING,
    LOADING_CONTEXT,
    REVIEWING,
    BUILDING_PREVIEW,
    SUCCESS,
    FAILED,
    CANCELLED
}
