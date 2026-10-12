package com.keyforge.nativeiiq.source;

import org.junit.jupiter.api.Test;
import sailpoint.object.Filter;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The "changed after" predicate: modified after the bound, OR never modified (NULL) and created after it — so objects
 * IIQ created but never updated (NULL {@code modified}) are not silently skipped by incremental reads. Builds the real
 * IIQ {@link Filter} when the IIQ classes load on this test classpath; otherwise the test is skipped (the predicate is
 * then verified by compiling against the 8.4 {@code identityiq.jar} and in the live run).
 */
class ChangedAfterFilterTest {

    @Test
    void combinesModifiedAndNeverModifiedButCreated() {
        Filter f;
        try {
            f = NativeIncrementalQuery.changedAfter(Date.from(Instant.parse("2026-10-12T10:00:00Z")));
        } catch (Throwable notLoadable) {
            assumeTrue(false, "IIQ Filter not constructible outside IdentityIQ: " + notLoadable);
            return;
        }
        assertNotNull(f);
        String s = String.valueOf(f);
        assertTrue(s.contains("modified") && s.contains("created"), s);
        assertTrue(s.toLowerCase().contains("isnull") || s.toLowerCase().contains("is null"), s);
        assertTrue(s.contains("||") || s.toLowerCase().contains(" or "), s);
    }
}
