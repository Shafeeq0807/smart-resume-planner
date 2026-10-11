package com.smartresume.planner.common.domain;

/** Lifecycle of any background unit of work (async jobs, GitHub analyses, PDF exports). */
public enum ProcessingStatus {
    PENDING, RUNNING, COMPLETED, FAILED
}
