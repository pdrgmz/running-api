package com.running.api.model;

/** Estado de un job de generación de plan. */
public enum TrainingJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMEOUT
}
