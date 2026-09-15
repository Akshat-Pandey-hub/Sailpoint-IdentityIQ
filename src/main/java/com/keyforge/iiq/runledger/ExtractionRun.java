package com.keyforge.iiq.runledger;

import java.time.Instant;

/**
 * An immutable {@code kf_extraction_run} ledger row: one per {@code -db} command execution.
 * Aggregate totals ({@code extracted/inserted/updated/failed}) plus a per-entity JSON breakdown
 * ({@code entityCountsJson}), run-level timing/window, source interface, status, and error info.
 */
public record ExtractionRun(
        String extractionRunId,
        String command,
        String sourceInterface,
        String status,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        Instant windowStart,
        Instant windowEnd,
        Integer extracted,
        Integer inserted,
        Integer updated,
        Integer failed,
        String entityCountsJson,
        String errorMessage) {
}
