package com.keyforge.iiq.runledger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable accumulator for a single {@code -db} command execution's extraction-run ledger entry.
 * Holds the run id, command, source interface, start time, optional extraction window, per-entity
 * counts, and an optional error message. One instance lives per run in {@link RunLedger}'s
 * thread-local while the command executes; it is turned into an immutable {@link ExtractionRun} and
 * persisted by {@link RunLedger#finish(int)}.
 *
 * <p>Reusable foundation: {@code runId} is the future lineage {@code extraction_run_id}, and the
 * per-entity counts feed reconciliation; nothing here implements SCD2/RAW/EVENT/watermarks.
 */
public final class RunContext {

    /** Per-entity/table extracted/inserted/updated/failed counts. */
    public record EntityCount(String entity, int extracted, int inserted, int updated, int failed) {
    }

    private final String runId;
    private final String command;
    private final String sourceInterface;
    private final Instant startedAt;
    private final List<EntityCount> entities = new ArrayList<>();
    private Instant windowStart;
    private Instant windowEnd;
    private String errorMessage;

    public RunContext(String runId, String command, String sourceInterface, Instant startedAt) {
        this.runId = runId;
        this.command = command;
        this.sourceInterface = sourceInterface;
        this.startedAt = startedAt;
    }

    public String runId() { return runId; }
    public String command() { return command; }
    public String sourceInterface() { return sourceInterface; }
    public Instant startedAt() { return startedAt; }
    public List<EntityCount> entities() { return entities; }
    public Instant windowStart() { return windowStart; }
    public Instant windowEnd() { return windowEnd; }
    public String errorMessage() { return errorMessage; }

    public void addEntity(String entity, int extracted, int inserted, int updated, int failed) {
        entities.add(new EntityCount(entity, extracted, inserted, updated, failed));
    }

    public void setWindow(Instant start, Instant end) {
        this.windowStart = start;
        this.windowEnd = end;
    }

    /** Records the first non-blank error message (later ones are appended). */
    public void setError(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        this.errorMessage = errorMessage == null ? message : errorMessage + " | " + message;
    }
}
