package com.running.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // --- Upload Metrics ---
    public void recordFileUpload(String fileType, boolean success) {
        Counter.builder("running.api.uploads")
                .tag("type", fileType)
                .tag("status", success ? "success" : "failure")
                .description("Total file uploads by type and status")
                .register(meterRegistry)
                .increment();
        log.debug("[METRICS] Upload registrado: type={}, success={}", fileType, success);
    }

    public void recordBulkUpload(int totalFiles, int successCount, int errorCount) {
        Counter.builder("running.api.bulk.uploads")
                .description("Total bulk upload operations")
                .register(meterRegistry)
                .increment();

        meterRegistry.gauge("running.api.bulk.last.total", totalFiles);
        meterRegistry.gauge("running.api.bulk.last.success", successCount);
        meterRegistry.gauge("running.api.bulk.last.errors", errorCount);
        log.debug("[METRICS] Bulk upload registrado: total={}, success={}, errors={}",
                totalFiles, successCount, errorCount);
    }

    // --- Activity Metrics ---
    public void recordActivityCreated(String sourceType) {
        Counter.builder("running.api.activities")
                .tag("source", sourceType)
                .description("Total activities created by source type")
                .register(meterRegistry)
                .increment();
        log.debug("[METRICS] Actividad creada: source={}", sourceType);
    }

    public void recordActivityDeleted() {
        Counter.builder("running.api.activities.deleted")
                .description("Total activities deleted")
                .register(meterRegistry)
                .increment();
    }

    // --- Parser Metrics ---
    public void recordParseTime(String fileType, long durationMs) {
        Timer.builder("running.api.parse.time")
                .tag("type", fileType)
                .description("File parse duration by type")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
        log.debug("[METRICS] Tiempo de parseo: type={}, duration={}ms", fileType, durationMs);
    }

    public void recordParseError(String fileType, String errorType) {
        Counter.builder("running.api.parse.errors")
                .tag("type", fileType)
                .tag("error", errorType)
                .description("Parse errors by file type and error type")
                .register(meterRegistry)
                .increment();
        log.warn("[METRICS] Error de parseo registrado: type={}, error={}", fileType, errorType);
    }

    // --- API Request Metrics ---
    public void recordApiCall(String endpoint, String method, int status, long durationMs) {
        Timer.builder("running.api.requests")
                .tag("endpoint", endpoint)
                .tag("method", method)
                .tag("status", String.valueOf(status))
                .description("API request duration by endpoint")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // --- Storage Metrics ---
    public void recordStorageOperation(String operation, boolean success) {
        Counter.builder("running.api.storage")
                .tag("operation", operation)
                .tag("status", success ? "success" : "failure")
                .description("Storage operations by type and status")
                .register(meterRegistry)
                .increment();
    }
}
