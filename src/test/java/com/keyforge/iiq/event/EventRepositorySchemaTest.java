package com.keyforge.iiq.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventRepositorySchemaTest {
    @Test
    void archiveSourceCanLiveInNativeSchemaWhileEventsStayInConfiguredSchema() {
        EventRepository repository = new EventRepository("iiq_migration_final", "iiq_native");
        assertEquals("iiq_migration_final.kf_event", repository.table());
        assertEquals("iiq_native.kf_workitem_archive", repository.workItemArchiveTable());
    }

    @Test
    void legacyConstructorKeepsAllSourcesInOneSchema() {
        EventRepository repository = new EventRepository("migration_test");
        assertEquals("migration_test.kf_workitem_archive", repository.workItemArchiveTable());
    }
}
