package com.keyforge.iiq;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the whole-native extraction plan consumed by {@code extract-native-db}: every job has a
 * runner, no command is listed twice, and the CSS/CEC classification is exactly the one the architecture
 * fixed (CSS = the five current-state entities that consume the shared incremental engine; CEC = the
 * append-only event/archive sources). Does NOT run any extractor — it only inspects the registry.
 */
class NativeExtractionRegistryTest {

    private static Set<String> commandsIn(Main.NativeExtractionCategory category) {
        return Main.nativeExtractionRegistry().stream()
                .filter(j -> j.category() == category)
                .map(Main.NativeExtractionJob::command)
                .collect(Collectors.toSet());
    }

    @Test
    void everyJobHasACommandAndRunner() {
        List<Main.NativeExtractionJob> jobs = Main.nativeExtractionRegistry();
        assertFalse(jobs.isEmpty(), "the native extraction plan must not be empty");
        for (Main.NativeExtractionJob job : jobs) {
            assertNotNull(job.command(), "command");
            assertNotNull(job.category(), "category");
            assertNotNull(job.runner(), "runner for " + job.command());
            assertTrue(job.command().startsWith("extract-native-"), "native command: " + job.command());
        }
    }

    @Test
    void noDuplicateCommands() {
        List<String> all = Main.nativeExtractionRegistry().stream()
                .map(Main.NativeExtractionJob::command).toList();
        assertEquals(all.size(), Set.copyOf(all).size(), "no command may appear twice in the plan");
    }

    @Test
    void cssIsExactlyTheFiveCurrentStateEntities() {
        assertEquals(
                Set.of("extract-native-identity-db", "extract-native-account-db", "extract-native-entitlement-db",
                        "extract-native-application-db", "extract-native-role-db"),
                commandsIn(Main.NativeExtractionCategory.CSS),
                "only the five current-state entities consume the shared CSS incremental engine");
    }

    @Test
    void cecIsExactlyTheAppendOnlyEventSources() {
        assertEquals(
                Set.of("extract-native-audit-events-db", "extract-native-syslog-events-db",
                        "extract-native-access-history-db", "extract-native-workitem-archive-db",
                        "extract-native-certification-archive-db"),
                commandsIn(Main.NativeExtractionCategory.CEC),
                "event/archive sources are append-only, never CSS modified-watermark");
    }

    @Test
    void parquetAndDerivationsAreNotExtractionJobs() {
        Set<String> all = Main.nativeExtractionRegistry().stream()
                .map(Main.NativeExtractionJob::command).collect(Collectors.toSet());
        assertFalse(all.contains("extract-native-parquet"), "parquet is a separate path, not in the PG plan");
        assertTrue(all.stream().noneMatch(c -> c.startsWith("derive-") || c.startsWith("reconcile-")),
                "derive/reconcile are not extraction jobs");
    }
}
