package com.keyforge.iiq.nativeparquet;

import com.keyforge.iiq.parquet.Column;
import com.keyforge.iiq.parquet.ParquetType;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The approved dataset set, their native (not REST) sources, and schema shape. */
class NativeParquetDatasetsTest {

    private final List<NativeParquetDatasets.Def> all = NativeParquetDatasets.all();

    @Test
    void exactlyTheTenApprovedDatasets_eventLinkOnce() {
        Set<String> names = all.stream().map(NativeParquetDatasets.Def::parquetName).collect(Collectors.toSet());
        assertEquals(Set.of("kf_cert_item_decision", "kf_access_request", "kf_request_item", "kf_request_approval",
                "kf_provisioning_txn", "kf_provisioning_item", "kf_event_link", "kf_violation", "kf_audit_event",
                "task_result"), names);
        long linkCount = all.stream().filter(d -> d.parquetName().equals("kf_event_link")).count();
        assertEquals(1, linkCount, "kf_event_link is ONE physical dataset, not duplicated");
    }

    @Test
    void sourcesAreNativeTablesNotRest() {
        NativeParquetDatasets.Def ar = def("kf_access_request");
        assertEquals("kf_identity_request", ar.sourceTable());        // native, not REST kf_access_request
        assertEquals("kf_task_result", def("task_result").sourceTable());
        assertEquals("kf_certification_item", def("kf_cert_item_decision").sourceTable());  // derived, no 2nd source table
    }

    @Test
    void certDecisionDatasetCarriesDecisionAndRevokeFields() {
        List<String> cols = def("kf_cert_item_decision").businessColumns();
        assertTrue(cols.containsAll(List.of("action_status", "action_decision_date", "action_remediation_action",
                "action_is_approved", "action_is_revoke_account", "acted_upon")));
    }

    @Test
    void relationshipEndpointIdColumnsPreserved() {
        assertTrue(def("kf_request_item").businessColumns().contains("request_source_id"));    // item→parent request
        assertTrue(def("kf_request_approval").businessColumns().contains("request_source_id"));
        assertTrue(def("kf_provisioning_item").businessColumns().contains("txn_source_id"));    // item→parent txn
        List<String> el = def("kf_event_link").businessColumns();
        assertTrue(el.containsAll(List.of("src_object_id", "target_object_id", "link_type")));  // explicit link ids
    }

    @Test
    void specIsBusinessColumnsPlusTwelveLineageColumns() {
        NativeParquetDatasets.Def v = def("kf_violation");
        List<Column> all = v.spec().allColumns();
        assertEquals(v.businessColumns().size() + 12, all.size());
        // business columns are STRING; lineage adds record_hash + extracted_at
        assertTrue(all.stream().anyMatch(c -> c.name().equals("record_hash")));
        assertTrue(all.stream().anyMatch(c -> c.name().equals("extracted_at") && c.type() == ParquetType.TIMESTAMP));
        assertTrue(v.spec().businessColumns().stream().allMatch(c -> c.type() == ParquetType.STRING));
    }

    @Test
    void eventLinkSchemaHasNoDuplicateColumnAndKeepsRequiredFields() {
        List<Column> cols = def("kf_event_link").spec().allColumns();
        List<String> names = cols.stream().map(Column::name).collect(Collectors.toList());
        // no column appears twice (the bug: src_object_type/src_object_id collided with lineage)
        assertEquals(names.size(), Set.copyOf(names).size(), "kf_event_link must not declare a column twice");
        // all required relationship + lineage-source fields present, exactly once each
        for (String required : List.of("src_object_id", "target_object_id", "link_type", "src_object_type",
                "target_object_type", "event_id", "link_status", "record_hash", "src_natural_key")) {
            assertEquals(1, names.stream().filter(required::equals).count(), "exactly one column: " + required);
        }
    }

    private NativeParquetDatasets.Def def(String name) {
        return all.stream().filter(d -> d.parquetName().equals(name)).findFirst().orElseThrow();
    }
}
