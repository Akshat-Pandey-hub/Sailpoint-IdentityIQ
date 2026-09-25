package com.keyforge.iiq.nativeparquet;

import com.keyforge.iiq.parquet.Column;
import com.keyforge.iiq.parquet.DatasetSpec;
import com.keyforge.iiq.parquet.ParquetType;

import java.util.ArrayList;
import java.util.List;

/**
 * The exactly-approved native-source Parquet datasets: each is derived from a native {@code iiq_native}
 * PostgreSQL table (NOT REST/SCIM), read current-state, and written as one stable Parquet file per
 * dataset (no per-run versioning). All business columns are STRING (the PG text form) for robustness;
 * the physical schema is these business columns followed by the shared 12-field lineage envelope, and
 * the lineage {@code record_hash} carries the content hash used for change detection.
 */
public final class NativeParquetDatasets {

    /**
     * @param parquetName  output dataset name (canonical HLD name)
     * @param sourceTable  native PG table (unqualified; resolved against the native schema)
     * @param pkColumn     the deterministic PK column used as the dedup identity (→ src_object_id)
     * @param srcObjectType lineage src_object_type
     * @param businessColumns ordered native columns preserved into the dataset
     */
    public record Def(String parquetName, String sourceTable, String pkColumn, String srcObjectType,
                      List<String> businessColumns) {

        /** DatasetSpec = the business columns (all STRING) + the shared lineage envelope. */
        public DatasetSpec spec() {
            List<Column> cols = new ArrayList<>();
            for (String c : businessColumns) {
                cols.add(Column.of(c, ParquetType.STRING));
            }
            return new DatasetSpec(parquetName, cols);
        }
    }

    private NativeParquetDatasets() {
    }

    public static List<Def> all() {
        List<Def> d = new ArrayList<>();

        // kf_cert_item_decision — DERIVED from native kf_certification_item (folded CertificationAction).
        // Undecided action_* remain NULL; never inferred.
        d.add(new Def("kf_cert_item_decision", "kf_certification_item", "certificationitemid",
                "sailpoint.object.CertificationItem", List.of(
                "source_id", "certification_id", "entity_id", "identity", "type", "bundle", "bundle_assignment_id",
                "exception_application", "exception_attribute_name", "exception_attribute_value", "summary_status",
                "phase", "reviewed", "acted_upon", "action_status", "action_decision_date", "action_actor_name",
                "action_owner_name", "action_remediation_action", "action_is_approved", "action_is_remediation",
                "action_is_revoke_account", "action_is_mitigation", "action_is_delegation", "action_is_auto_decision",
                "action_is_bulk_certified", "owner_id", "owner_name")));

        // kf_access_request — native kf_identity_request.
        d.add(new Def("kf_access_request", "kf_identity_request", "identityrequestid",
                "sailpoint.object.IdentityRequest", List.of(
                "source_id", "name", "type", "state", "completion_status", "execution_status", "priority",
                "requester_id", "requester_display_name", "target_id", "target_display_name", "external_ticket_id",
                "process_id", "task_result_id", "end_date", "created_at", "owner_id", "owner_name",
                "item_count", "approval_count")));

        // kf_request_item — native kf_identity_request_item (explicit parent request_source_id).
        d.add(new Def("kf_request_item", "kf_identity_request_item", "identityrequestitemid",
                "sailpoint.object.IdentityRequestItem", List.of(
                "source_id", "request_source_id", "request_name", "application", "attribute_name", "attribute_value",
                "operation", "assignment_id", "native_identity", "instance", "approver_name", "approval_state",
                "approved", "provisioning_state", "provisioning_complete", "compilation_status", "owner_name",
                "start_date", "end_date")));

        // kf_request_approval — native kf_identity_request_approval (explicit request + work item).
        d.add(new Def("kf_request_approval", "kf_identity_request_approval", "identityrequestapprovalid",
                "sailpoint.object.IdentityRequest.Approval", List.of(
                "request_source_id", "request_name", "work_item_id", "work_item_type", "owner", "owner_id",
                "completer", "approved", "state", "start_date", "end_date", "approval_item_count", "approval_index")));

        // kf_provisioning_txn — native.
        d.add(new Def("kf_provisioning_txn", "kf_provisioning_txn", "provisioningtxnid",
                "sailpoint.object.ProvisioningTransaction", List.of(
                "source_id", "name", "operation", "type", "status", "source", "identity_name", "application_name",
                "native_identity", "account_display_name", "certification_id", "access_request_id",
                "wait_work_item_id", "manual_work_item_id", "request_id", "item_count", "owner_id", "owner_name",
                "created_at")));

        // kf_provisioning_item — native (explicit parent txn_source_id).
        d.add(new Def("kf_provisioning_item", "kf_provisioning_item", "provisioningitemid",
                "sailpoint.object.ProvisioningTransaction.Item", List.of(
                "txn_source_id", "identity_name", "item_type", "operation", "application_name", "native_identity",
                "instance", "account_operation", "name", "value", "assignment_id", "permission_target",
                "permission_rights", "request_id", "item_index")));

        // kf_event_link — native (explicit source + target ids + link type). ONE physical dataset.
        d.add(new Def("kf_event_link", "kf_event_link", "link_id", "kf_event_link", List.of(
                "event_id", "src_object_type", "src_object_id", "target_object_type", "target_object_id",
                "link_type", "link_status")));

        // kf_violation — native PolicyViolation (0 rows currently; do not fabricate).
        d.add(new Def("kf_violation", "kf_violation", "violationid", "sailpoint.object.PolicyViolation", List.of(
                "source_id", "name", "identity_id", "identity_name", "policy_id", "policy_name", "constraint_id",
                "constraint_name", "status", "active")));

        // kf_audit_event — native AuditEvent (append-only source evidence).
        d.add(new Def("kf_audit_event", "kf_audit_event", "auditid", "sailpoint.object.AuditEvent", List.of(
                "source_id", "action", "audit_source", "target", "application", "account_name", "instance",
                "attribute_name", "attribute_value", "interface_name", "server_host", "tracking_id", "created_at")));

        // task_result — existing native kf_task_result.
        d.add(new Def("task_result", "kf_task_result", "taskresultid", "sailpoint.object.TaskResult", List.of(
                "source_id", "name", "type", "completion_status", "definition_name", "launcher", "host",
                "target_name", "launched_at", "completed_at", "created_at")));

        return d;
    }
}
