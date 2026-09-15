package com.keyforge.iiq.parquet;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the modular Parquet command architecture: every individual command maps to a real dataset,
 * every dataset has exactly one command, and {@code extract-all-parquet} is orchestration only.
 */
class ParquetCommandsTest {

    private static final List<String> EXPECTED_DATASETS = List.of(
            "task_result", "kf_audit_event", "kf_access_request", "kf_request_item", "kf_request_approval",
            "kf_provisioning_txn", "kf_provisioning_item", "kf_violation", "kf_cert_item_decision", "kf_event_link");

    @Test
    void tenIndividualCommandsOneAll() {
        assertEquals(10, ParquetCommands.INDIVIDUAL.size());
        assertEquals("extract-all-parquet", ParquetCommands.ALL);
        assertNull(ParquetCommands.datasetFor("extract-users-db")); // a DB command is not a parquet command
        assertNull(ParquetCommands.datasetFor("extract-all-parquet")); // ALL is not an individual command
    }

    @Test
    void everyCommandMapsToARealRegistryDataset() {
        Set<String> registry = new HashSet<>(new ParquetDatasets().names());
        for (String dataset : ParquetCommands.INDIVIDUAL.values()) {
            assertTrue(registry.contains(dataset), "command maps to unknown dataset: " + dataset);
        }
    }

    @Test
    void everyRegistryDatasetHasExactlyOneCommand() {
        Set<String> commanded = new HashSet<>(ParquetCommands.INDIVIDUAL.values());
        assertEquals(EXPECTED_DATASETS.size(), commanded.size(), "duplicate or missing dataset commands");
        assertEquals(new HashSet<>(EXPECTED_DATASETS), commanded);
        // and the registry itself contains exactly those datasets
        assertEquals(new HashSet<>(EXPECTED_DATASETS), new HashSet<>(new ParquetDatasets().names()));
    }

    @Test
    void namingFollowsProjectConvention() {
        for (String command : ParquetCommands.INDIVIDUAL.keySet()) {
            assertTrue(command.startsWith("extract-") && command.endsWith("-parquet"),
                    "unexpected command name: " + command);
        }
    }
}
