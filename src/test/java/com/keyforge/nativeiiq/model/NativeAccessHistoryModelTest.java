package com.keyforge.nativeiiq.model;

import com.keyforge.nativeiiq.wire.NativeSerialize;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-POJO checks for the native Access-History model + the enum/name serialization the mappers rely on.
 * Deliberately avoids constructing AspectJ-woven SailPoint objects (getter correctness against the real
 * {@code identityiq.jar} is validated by the {@code native} compile, not here): every native mapper is a
 * straight getter-to-setter copy. Verifies the lineage contract, NULL defaults, enum {@code name()}
 * rendering, and the run-result aggregation across the three object types.
 */
class NativeAccessHistoryModelTest {

    @Test
    void lineageConstantsAndNullDefaults() {
        NativeHistEntitlementCaptureRow ent = new NativeHistEntitlementCaptureRow();
        assertEquals("native_iiq_java_api", ent.getSrcInterface());
        assertEquals("sailpoint.object.accesshistory.HistoricalEntitlementCapture", ent.getSrcObjectType());
        assertNull(ent.getSourceId());           // nothing fabricated before a source object is mapped
        assertNull(ent.getEffectiveDate());
        assertTrue(ent.getAttributes().isEmpty());
        assertTrue(ent.getExtendedAttributes().isEmpty());
        assertTrue(ent.getCompressiblePropertyNames().isEmpty());

        NativeHistIdentityEventRow evt = new NativeHistIdentityEventRow();
        assertEquals("native_iiq_java_api", evt.getSrcInterface());
        assertEquals("sailpoint.object.accesshistory.HistoricalIdentityEvent", evt.getSrcObjectType());
        assertNull(evt.getEventType());

        NativeHistCertificationRow cert = new NativeHistCertificationRow();
        assertEquals("native_iiq_java_api", cert.getSrcInterface());
        assertEquals("sailpoint.object.accesshistory.HistoricalCertification", cert.getSrcObjectType());
        assertNull(cert.getCertId());
    }

    @Test
    void enumNameRenderingMatchesMapperContract() {
        // the mappers store event enums as their source name(); null enum -> null (NULL preserved)
        assertEquals("MONDAY", NativeSerialize.enumName(DayOfWeek.MONDAY));
        assertNull(NativeSerialize.enumName(null));
        assertEquals("x", NativeSerialize.enumName("x"));
    }

    @Test
    void resultAggregatesThreeObjectTypes() {
        NativeAccessHistoryExtractionResult r =
                new NativeAccessHistoryExtractionResult("IdentityIQ", "run-1", Instant.now());
        assertEquals("IdentityIQ", r.getSourceSystem());
        assertEquals("run-1", r.getExtractionRunId());
        assertEquals(0, r.getTotalCount());

        r.getEntitlementCaptures().add(new NativeHistEntitlementCaptureRow());
        r.getEntitlementCaptures().add(new NativeHistEntitlementCaptureRow());
        r.getIdentityEvents().add(new NativeHistIdentityEventRow());
        r.getCertifications().add(new NativeHistCertificationRow());
        r.setEntitlementCaptureSourceCount(2);
        r.setIdentityEventSourceCount(5);
        r.setCertificationSourceCount(1);

        assertEquals(2, r.getEntitlementCaptureCount());
        assertEquals(1, r.getIdentityEventCount());
        assertEquals(1, r.getCertificationCount());
        assertEquals(4, r.getTotalCount());
        assertEquals(2, r.getEntitlementCaptureSourceCount());
        assertEquals(5, r.getIdentityEventSourceCount());  // source has more than this page extracted
        assertEquals(1, r.getCertificationSourceCount());
    }
}
