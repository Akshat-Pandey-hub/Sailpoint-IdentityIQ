package com.keyforge.iiq.nativeparquet;

import com.keyforge.iiq.client.IiqSessionClient;
import com.keyforge.nativeload.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Full-scan source backed by the existing KF Agent page clients and REST services. */
public final class AgentParquetRowSource implements ParquetRowSource {
    @FunctionalInterface
    public interface AgentPageSource {
        List<Map<String, Object>> fetch(String service, NativeParquetDatasets.Def dataset) throws Exception;
    }

    private final AgentPageSource pages;

    public AgentParquetRowSource(IiqSessionClient session) {
        this((service, d) -> fetchFromServices(session, service));
    }

    public AgentParquetRowSource(AgentPageSource pages) {
        this.pages = pages;
    }

    @Override
    public List<Row> read(NativeParquetDatasets.Def d) throws Exception {
        String service = serviceFor(d.parquetName());
        if (service == null) {
            throw new IllegalStateException("SKIPPED: no KF Agent service for dataset " + d.parquetName()
                    + " (derived only in PostgreSQL)");
        }
        List<Map<String, Object>> data = pages.fetch(service, d);
        List<Row> rows = new ArrayList<>(data.size());
        for (Map<String, Object> map : data) {
            List<String> required = new ArrayList<>(d.businessColumns());
            switch (d.parquetName()) {
                case "kf_request_approval" -> required.addAll(List.of("work_item_id", "owner", "approval_index"));
                case "kf_provisioning_item" -> required.add("value_json");
                default -> { }
            }
            List<String> missing = required.stream().distinct().filter(c -> !map.containsKey(c)).toList();
            if (!missing.isEmpty()) {
                throw new IllegalStateException("KF Agent service " + service + " is missing business columns: " + missing);
            }
            rows.add(new Row(NativeParquetRowKeys.key(d.parquetName(), map), map,
                    parseInstant(map.get("created_at")), parseInstant(map.get("modified_at"))));
        }
        return rows;
    }

    public static String serviceFor(String dataset) {
        return switch (dataset) {
            case "kf_cert_item_decision" -> "certification-items";
            case "kf_access_request" -> "identity-requests";
            case "kf_request_item" -> "identity-request-items";
            case "kf_request_approval" -> "identity-request-approvals";
            case "kf_provisioning_txn" -> "provisioning-transactions";
            case "kf_provisioning_item" -> "provisioning-items";
            case "kf_violation" -> "policy-violations";
            case "kf_audit_event" -> "audit-events";
            case "task_result" -> "task-results";
            default -> null;
        };
    }

    private static List<Map<String, Object>> fetchFromServices(IiqSessionClient session, String service) {
        Map<String, String> noFilters = Map.of();
        return switch (service) {
            case "certification-items" -> new NativeCertificationItemRestService().fetch(
                    new NativeCertificationItemClient(session), noFilters, null, null);
            case "identity-requests" -> new NativeIdentityRequestRestService().fetch(
                    new NativeIdentityRequestClient(session), noFilters, null, null);
            case "identity-request-items" -> new NativeIdentityRequestItemRestService().fetch(
                    new NativeIdentityRequestClient(session), noFilters, null, null);
            case "identity-request-approvals" -> new NativeIdentityRequestApprovalRestService().fetch(
                    new NativeIdentityRequestClient(session), noFilters, null, null);
            case "provisioning-transactions" -> new NativeProvisioningTransactionRestService().fetch(
                    new NativeProvisioningTxnClient(session), noFilters, null, null);
            case "provisioning-items" -> new NativeProvisioningItemRestService().fetch(
                    new NativeProvisioningTxnClient(session), noFilters, null, null);
            case "policy-violations" -> new NativeViolationRestService().fetch(
                    new NativeViolationClient(session), noFilters, null, null);
            case "audit-events" -> new NativeAuditEventRestService().fetch(
                    new NativeAuditEventClient(session), noFilters, null, null);
            case "task-results" -> new NativeTaskResultRestService().fetch(
                    new NativeTaskResultClient(session), noFilters, null, null);
            default -> throw new IllegalArgumentException("Unsupported KF Agent service " + service);
        };
    }

    private static Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        String text = value.toString();
        try { return Instant.parse(text); }
        catch (RuntimeException ignored) { }
        try { return OffsetDateTime.parse(text).toInstant(); }
        catch (RuntimeException ignored) { }
        return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
    }
}
