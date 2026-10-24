package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import java.util.Map;

/** Adapts Agent row maps to the same repository key functions used by the PostgreSQL import path. */
public final class NativeParquetRowKeys {
    private NativeParquetRowKeys() { }

    public static String key(String dataset, Map<String, Object> row) {
        return switch (dataset) {
            case "kf_cert_item_decision" -> {
                NativeCertificationItemRecord r = new NativeCertificationItemRecord();
                r.sourceId = text(row, "source_id");
                yield NativeCertificationItemRepository.canonicalItemId(r);
            }
            case "kf_access_request" -> {
                NativeIdentityRequestRecord r = new NativeIdentityRequestRecord();
                r.sourceId = text(row, "source_id");
                yield NativeIdentityRequestRepository.canonicalRequestId(r);
            }
            case "kf_request_item" -> {
                NativeIdentityRequestItemRecord r = new NativeIdentityRequestItemRecord();
                r.sourceId = text(row, "source_id");
                yield NativeIdentityRequestItemRepository.canonicalItemId(r);
            }
            case "kf_request_approval" -> {
                NativeIdentityRequestApprovalRecord r = new NativeIdentityRequestApprovalRecord();
                r.requestSourceId = text(row, "request_source_id");
                r.workItemId = text(row, "work_item_id");
                r.owner = text(row, "owner");
                r.approvalIndex = integer(row.get("approval_index"));
                yield NativeIdentityRequestApprovalRepository.canonicalApprovalId(r);
            }
            case "kf_provisioning_txn" -> {
                NativeProvisioningTxnRecord r = new NativeProvisioningTxnRecord();
                r.sourceId = text(row, "source_id");
                yield NativeProvisioningTxnRepository.canonicalTxnId(r);
            }
            case "kf_provisioning_item" -> {
                NativeProvisioningItemRecord r = new NativeProvisioningItemRecord();
                r.txnSourceId = text(row, "txn_source_id");
                r.itemType = text(row, "item_type");
                r.operation = text(row, "operation");
                r.name = text(row, "name");
                r.value = text(row, "value");
                r.valueJson = text(row, "value_json");
                r.itemIndex = integer(row.get("item_index"));
                yield NativeProvisioningItemRepository.canonicalItemId(r);
            }
            case "kf_violation" -> {
                NativeViolationRecord r = new NativeViolationRecord();
                r.sourceId = text(row, "source_id");
                r.name = text(row, "name");
                yield NativeViolationRepository.canonicalViolationId(r);
            }
            case "kf_audit_event" -> {
                NativeAuditEventRecord r = new NativeAuditEventRecord();
                r.sourceId = text(row, "source_id");
                r.action = text(row, "action");
                yield NativeAuditEventRepository.canonicalAuditId(r);
            }
            case "task_result" -> {
                NativeTaskResultRecord r = new NativeTaskResultRecord();
                r.sourceId = text(row, "source_id");
                r.name = text(row, "name");
                yield NativeTaskResultRepository.canonicalTaskResultId(r);
            }
            default -> throw new IllegalArgumentException("No native Agent key mapping for dataset " + dataset);
        };
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static Integer integer(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        return Integer.valueOf(value.toString());
    }
}
