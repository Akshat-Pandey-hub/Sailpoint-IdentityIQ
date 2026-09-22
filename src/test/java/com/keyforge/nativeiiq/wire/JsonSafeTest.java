package com.keyforge.nativeiiq.wire;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link JsonSafe} degrades any value to a JSON-serializable form and never throws — the
 * property that keeps the native REST endpoint from failing on an unusual Identity extended attribute.
 */
class JsonSafeTest {

    private enum Color { RED }

    /** A bean with no serializable properties — Jackson's default mapper throws on this ("empty bean"). */
    private static final class Opaque {
        @SuppressWarnings("unused")
        private final int hidden = 1;
    }

    /** A value whose toString() blows up — the conversion must still not throw. */
    private static final class Boom {
        @Override public String toString() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void passesThroughJsonPrimitivesAndNull() {
        assertNull(JsonSafe.toJsonSafe(null));
        assertEquals("x", JsonSafe.toJsonSafe("x"));
        assertEquals(Boolean.TRUE, JsonSafe.toJsonSafe(Boolean.TRUE));
        assertEquals(Integer.valueOf(42), JsonSafe.toJsonSafe(Integer.valueOf(42)));
        assertEquals(Long.valueOf(7L), JsonSafe.toJsonSafe(Long.valueOf(7L)));
    }

    @Test
    void convertsDatesEnumsCollectionsMapsAndArrays() {
        Object date = JsonSafe.toJsonSafe(new Date(0L));
        assertInstanceOf(String.class, date);
        assertEquals("1970-01-01T00:00:00Z", date);

        assertEquals("RED", JsonSafe.toJsonSafe(Color.RED));

        Object list = JsonSafe.toJsonSafe(Arrays.asList("a", Color.RED));
        assertInstanceOf(List.class, list);
        assertEquals(Arrays.asList("a", "RED"), list);

        Object arr = JsonSafe.toJsonSafe(new Object[]{"a", Integer.valueOf(1)});
        assertEquals(Arrays.asList("a", Integer.valueOf(1)), arr);

        Map<Object, Object> raw = new LinkedHashMap<>();
        raw.put("k", Color.RED);
        raw.put(Integer.valueOf(2), "v"); // non-String key must become a String key
        Object converted = JsonSafe.toJsonSafe(raw);
        assertInstanceOf(Map.class, converted);
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) converted;
        assertEquals("RED", m.get("k"));
        assertEquals("v", m.get("2"));
    }

    @Test
    void degradesUnknownBeanToStringInsteadOfLeavingItRaw() {
        Object safe = JsonSafe.toJsonSafe(new Opaque());
        assertInstanceOf(String.class, safe, "an opaque bean must be degraded to a string");
    }

    @Test
    void neverThrowsEvenWhenToStringThrows() {
        Object safe = assertDoesNotThrow(() -> JsonSafe.toJsonSafe(new Boom()));
        assertInstanceOf(String.class, safe);
        assertTrue(((String) safe).startsWith("<unserializable:"));
    }

    @Test
    void convertedValuesSerializeWithJacksonWhereRawValuesWouldFail() throws Exception {
        // Jackson's default mapper (FAIL_ON_EMPTY_BEANS enabled, like IIQ's JsonHelper) throws on a raw
        // opaque bean...
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> rawAttrs = new LinkedHashMap<>();
        rawAttrs.put("weird", new Opaque());
        assertThrows(Exception.class, () -> mapper.writeValueAsString(rawAttrs));

        // ...but succeeds once every value has passed through JsonSafe.
        Map<String, Object> safeAttrs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : rawAttrs.entrySet()) {
            safeAttrs.put(e.getKey(), JsonSafe.toJsonSafe(e.getValue()));
        }
        String json = assertDoesNotThrow(() -> mapper.writeValueAsString(safeAttrs));
        assertTrue(json.contains("weird"));
    }
}
