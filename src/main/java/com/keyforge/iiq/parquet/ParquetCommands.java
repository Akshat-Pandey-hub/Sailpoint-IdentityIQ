package com.keyforge.iiq.parquet;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CLI command &rarr; Parquet dataset mapping. One independent extraction command per dataset (mirroring
 * the per-entity PostgreSQL extractors), plus {@link #ALL} which orchestrates them. This map is the
 * single source of truth used by both the CLI dispatch and the tests, so the two cannot drift.
 */
public final class ParquetCommands {

    /** Orchestration command: runs every individual Parquet extractor. */
    public static final String ALL = "extract-all-parquet";

    /** Individual command &rarr; dataset name. Order defines the {@link #ALL} run order. */
    public static final Map<String, String> INDIVIDUAL;

    static {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("extract-task-results-parquet", "task_result");
        m.put("extract-audit-events-parquet", "kf_audit_event");
        m.put("extract-access-requests-parquet", "kf_access_request");
        m.put("extract-request-items-parquet", "kf_request_item");
        m.put("extract-request-approvals-parquet", "kf_request_approval");
        m.put("extract-provisioning-transactions-parquet", "kf_provisioning_txn");
        m.put("extract-provisioning-items-parquet", "kf_provisioning_item");
        m.put("extract-violations-parquet", "kf_violation");
        m.put("extract-cert-item-decisions-parquet", "kf_cert_item_decision");
        m.put("extract-event-links-parquet", "kf_event_link");
        INDIVIDUAL = java.util.Collections.unmodifiableMap(m);
    }

    private ParquetCommands() {
    }

    /** The dataset for an individual command, or {@code null} if the command is not a Parquet command. */
    public static String datasetFor(String command) {
        return INDIVIDUAL.get(command);
    }
}
