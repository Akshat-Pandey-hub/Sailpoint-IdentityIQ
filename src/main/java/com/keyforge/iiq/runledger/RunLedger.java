package com.keyforge.iiq.runledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.config.PgConfig;
import com.keyforge.iiq.db.PostgresConnection;

import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thread-local facade for the extraction-run ledger. A {@code -db} command's execution is wrapped by
 * {@link #run(String, java.util.function.IntSupplier)} (or {@link #begin}/{@link #finish}); runners
 * report per-entity counts via {@link #record} and optional error detail via {@link #error}.
 *
 * <p>Design notes: exactly one run id per {@code -db} command execution; the row is persisted even
 * when the command fails (status FAILED, error captured), via a fresh connection so a broken
 * extraction connection does not prevent recording. All reporting methods are safe no-ops when no
 * run is active, so non-{@code -db} commands, unit tests, and library callers are unaffected. This is
 * the reusable foundation for the PDF's lineage ({@code extraction_run_id}), reconciliation
 * (per-entity counts), and incremental-sync (window) requirements — none of those pillars are
 * implemented here.
 */
public final class RunLedger {

    private static final ThreadLocal<RunContext> CURRENT = new ThreadLocal<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RunLedger() {
    }

    // --- lifecycle ----------------------------------------------------------

    /** Wraps a {@code -db} runner: begins a run, executes it, and finishes (persists) around it. */
    public static int run(String command, java.util.function.IntSupplier runner) {
        begin(command);
        int exitCode = 1;
        try {
            exitCode = runner.getAsInt();
            return exitCode;
        } finally {
            finish(exitCode);
        }
    }

    public static void begin(String command) {
        CURRENT.set(new RunContext(UUID.randomUUID().toString(), command, interfaceFor(command), Instant.now()));
    }

    /** Builds and best-effort persists the ledger row, then clears the thread-local. Never throws. */
    public static void finish(int exitCode) {
        RunContext ctx = CURRENT.get();
        if (ctx == null) {
            return;
        }
        try {
            ExtractionRun run = buildRun(ctx, exitCode, Instant.now());
            persist(run, ctx.command());
        } catch (RuntimeException unexpected) {
            System.err.println("[run-ledger] failed to build/record run for '" + ctx.command()
                    + "': " + unexpected.getMessage());
        } finally {
            CURRENT.remove();
        }
    }

    // --- reporting (safe no-ops when no run is active) ----------------------

    public static void record(String entity, int extracted, int inserted, int updated, int failed) {
        RunContext ctx = CURRENT.get();
        if (ctx != null) {
            ctx.addEntity(entity, extracted, inserted, updated, failed);
        }
    }

    public static void error(String message) {
        RunContext ctx = CURRENT.get();
        if (ctx != null) {
            ctx.setError(message);
        }
    }

    public static void window(Instant start, Instant end) {
        RunContext ctx = CURRENT.get();
        if (ctx != null) {
            ctx.setWindow(start, end);
        }
    }

    /** The current run's id, or {@code null} outside a wrapped {@code -db} command. */
    public static String currentRunId() {
        RunContext ctx = CURRENT.get();
        return ctx == null ? null : ctx.runId();
    }

    // --- helpers ------------------------------------------------------------

    /** Pure: turns a {@link RunContext} + exit code into the immutable ledger row (unit-testable). */
    static ExtractionRun buildRun(RunContext ctx, int exitCode, Instant ended) {
        int extracted = 0;
        int inserted = 0;
        int updated = 0;
        int failed = 0;
        for (RunContext.EntityCount e : ctx.entities()) {
            extracted += e.extracted();
            inserted += e.inserted();
            updated += e.updated();
            failed += e.failed();
        }
        String status = statusFor(exitCode, failed);
        String error = ctx.errorMessage();
        if (error == null && !"SUCCESS".equals(status)) {
            error = reasonFor(exitCode) + " (exit code " + exitCode + ")";
        }
        boolean noEntities = ctx.entities().isEmpty();
        return new ExtractionRun(
                ctx.runId(), ctx.command(), ctx.sourceInterface(), status,
                ctx.startedAt(), ended, ended.toEpochMilli() - ctx.startedAt().toEpochMilli(),
                ctx.windowStart(), ctx.windowEnd(),
                noEntities ? null : extracted,
                noEntities ? null : inserted,
                noEntities ? null : updated,
                noEntities ? null : failed,
                entityCountsJson(ctx), error);
    }

    private static void persist(ExtractionRun run, String command) {
        try {
            PgConfig pg = PgConfig.load();
            try (Connection conn = PostgresConnection.open(pg)) {
                RunLedgerRepository repo = new RunLedgerRepository(pg.getSchema());
                repo.ensureTargetTable(conn);
                repo.insert(conn, run);
            }
        } catch (Exception e) {
            // Best-effort: never mask the command's own exit code (e.g. PG unreachable on a failed run).
            System.err.println("[run-ledger] could not record extraction run " + run.extractionRunId()
                    + " (" + command + "): " + e.getMessage());
        }
    }

    /** Human reason per the CLI's exit-code convention (see Main runners). */
    private static String reasonFor(int exitCode) {
        return switch (exitCode) {
            case 3 -> "configuration error";
            case 4 -> "IdentityIQ API error";
            case 5 -> "PostgreSQL error";
            case 6 -> "completed with row-level failures";
            default -> "unexpected error";
        };
    }

    private static String statusFor(int exitCode, int failed) {
        if (exitCode == 0) {
            return failed > 0 ? "COMPLETED_WITH_FAILURES" : "SUCCESS";
        }
        if (exitCode == 6) {
            return "COMPLETED_WITH_FAILURES";
        }
        return "FAILED";
    }

    private static String entityCountsJson(RunContext ctx) {
        if (ctx.entities().isEmpty()) {
            return null;
        }
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (RunContext.EntityCount e : ctx.entities()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entity", e.entity());
            m.put("extracted", e.extracted());
            m.put("inserted", e.inserted());
            m.put("updated", e.updated());
            m.put("failed", e.failed());
            list.add(m);
        }
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    /** Coarse source-interface tag per command (metadata only). */
    private static String interfaceFor(String command) {
        return switch (command) {
            case "extract-users-db", "extract-applications-db", "extract-accounts-db",
                 "extract-entitlements-db", "extract-roles-db", "extract-role-hierarchy-db",
                 "extract-workflows-db", "extract-policy-violations-db", "extract-task-results-db" -> "scim";
            case "extract-access-requests-db", "extract-workitems-db" -> "ui-rest";
            case "extract-provisioning-transactions-db", "derive-provisioning-links-db",
                 "extract-certifications-db", "extract-provisioning-items-db",
                 "extract-identity-roles-db" -> "classic-rest";
            case "extract-native-identity-db", "extract-native-entitlement-db",
                 "extract-native-application-db", "extract-native-account-db",
                 "extract-native-role-db", "extract-native-workgroup-db",
                 "extract-native-group-definition-db", "extract-native-workitem-archive-db",
                 "extract-native-workflows-db", "extract-native-workgroup-memberships-db",
                 "extract-native-identity-entitlement-db", "extract-native-account-entitlement-db",
                 "extract-native-certification-db", "extract-native-certification-entity-db",
                 "extract-native-certification-item-db", "extract-native-certification-archive-db",
                 "extract-native-provisioning-txn-db", "extract-native-identity-request-db",
                 "extract-native-workitem-db", "extract-native-role-relationships-db",
                 "extract-native-identity-role-db", "extract-native-task-result-db",
                 "extract-native-task-schedule-db" -> "native-plugin-rest";
            case "extract-usergroups-db", "extract-workgroups-db", "extract-workgroup-members-db",
                 "extract-policies-db", "extract-audit-events-db", "extract-role-entitlements-db" -> "classic-ui";
            case "extract-applicationinstances-db", "extract-assignments-db", "extract-catalog-db",
                 "extract-account-entitlements-db", "extract-identity-entitlements-db",
                 "extract-object-owners-db", "derive-event-links-db", "derive-events-db",
                 "reconcile-canonical-views-db",
                 "reconcile-referential-integrity-db", "derive-record-lineage-db",
                 "reconcile-counts-db" -> "derived";
            default -> "unknown";
        };
    }
}
