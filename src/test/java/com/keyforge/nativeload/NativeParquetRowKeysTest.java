package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeParquetRowKeysTest {
    @Test
    void usesTheImportRepositoriesCanonicalKeyForEveryAgentBackedDataset() {
        Map<String, Object> row = new HashMap<>();
        row.put("source_id", "550e8400-e29b-41d4-a716-446655440000");
        row.put("action", "Action");
        row.put("name", "name");
        row.put("request_source_id", "request-1");
        row.put("work_item_id", "work-1");
        row.put("owner", "owner");
        row.put("approval_index", 2);
        row.put("txn_source_id", "txn-1");
        row.put("item_type", "AttributeRequest");
        row.put("operation", "Set");
        row.put("value", "value");
        row.put("value_json", "{\"v\":\"value\"}");
        row.put("item_index", 3);

        NativeCertificationItemRecord cert = new NativeCertificationItemRecord();
        cert.sourceId = "550e8400-e29b-41d4-a716-446655440000";
        assertEquals(NativeCertificationItemRepository.canonicalItemId(cert),
                NativeParquetRowKeys.key("kf_cert_item_decision", row));
        NativeIdentityRequestRecord request = new NativeIdentityRequestRecord();
        request.sourceId = "550e8400-e29b-41d4-a716-446655440000";
        assertEquals(NativeIdentityRequestRepository.canonicalRequestId(request),
                NativeParquetRowKeys.key("kf_access_request", row));
        NativeIdentityRequestItemRecord requestItem = new NativeIdentityRequestItemRecord();
        requestItem.sourceId = "550e8400-e29b-41d4-a716-446655440000";
        assertEquals(NativeIdentityRequestItemRepository.canonicalItemId(requestItem),
                NativeParquetRowKeys.key("kf_request_item", row));
        NativeIdentityRequestApprovalRecord approval = new NativeIdentityRequestApprovalRecord();
        approval.requestSourceId = "request-1";
        approval.workItemId = "work-1";
        approval.owner = "owner";
        approval.approvalIndex = 2;
        assertEquals(NativeIdentityRequestApprovalRepository.canonicalApprovalId(approval),
                NativeParquetRowKeys.key("kf_request_approval", row));
        NativeProvisioningTxnRecord txn = new NativeProvisioningTxnRecord();
        txn.sourceId = "550e8400-e29b-41d4-a716-446655440000";
        assertEquals(NativeProvisioningTxnRepository.canonicalTxnId(txn),
                NativeParquetRowKeys.key("kf_provisioning_txn", row));
        NativeProvisioningItemRecord item = new NativeProvisioningItemRecord();
        item.txnSourceId = "txn-1";
        item.itemType = "AttributeRequest";
        item.operation = "Set";
        item.name = "name";
        item.value = "value";
        item.valueJson = "{\"v\":\"value\"}";
        item.itemIndex = 3;
        assertEquals(NativeProvisioningItemRepository.canonicalItemId(item),
                NativeParquetRowKeys.key("kf_provisioning_item", row));
        NativeViolationRecord violation = new NativeViolationRecord();
        violation.sourceId = "550e8400-e29b-41d4-a716-446655440000";
        violation.name = "name";
        assertEquals(NativeViolationRepository.canonicalViolationId(violation),
                NativeParquetRowKeys.key("kf_violation", row));
        NativeAuditEventRecord audit = new NativeAuditEventRecord();
        audit.sourceId = "550e8400-e29b-41d4-a716-446655440000";
        audit.action = "Action";
        assertEquals(NativeAuditEventRepository.canonicalAuditId(audit),
                NativeParquetRowKeys.key("kf_audit_event", row));
        NativeTaskResultRecord task = new NativeTaskResultRecord();
        task.sourceId = "550e8400-e29b-41d4-a716-446655440000";
        task.name = "name";
        assertEquals(NativeTaskResultRepository.canonicalTaskResultId(task),
                NativeParquetRowKeys.key("task_result", row));
    }
}
