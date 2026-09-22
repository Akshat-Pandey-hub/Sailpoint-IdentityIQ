package com.keyforge.nativeiiq.wire;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts an arbitrary value (typically a raw entry from {@code sailpoint.object.Identity.getAttributes()})
 * into a strictly JSON-safe value: only {@code null}, String, Boolean, Number, and nested Lists/Maps of
 * the same. This is what lets the native REST endpoint serialize identity extended attributes with IIQ's
 * Jackson-based {@code JsonHelper} without risking a serialization exception on an unexpected type
 * (SailPoint object, Hibernate proxy, enum, byte[], …), which the default Jackson mapper would otherwise
 * throw on (its {@code FAIL_ON_EMPTY_BEANS} is enabled).
 *
 * <p><b>Never throws.</b> Anything it cannot represent structurally is degraded to a safe
 * {@code String.valueOf(...)} rather than lost, and any error during conversion of a single value yields
 * a small placeholder string — one unusual attribute can never fail the whole identity.
 *
 * <p><b>Call while the source object is still attached to its session</b> (i.e. before the extractor
 * {@code decache}s the Identity): converting here forces any lazy value to materialize into plain data,
 * so the returned graph is safe to serialize later even after the Identity is detached.
 */
public final class JsonSafe {

    /** Guard against pathological/cyclic attribute graphs. */
    static final int MAX_DEPTH = 12;

    private JsonSafe() {
    }

    /** @return a JSON-safe representation (null / String / Boolean / Number / List / Map). Never throws. */
    public static Object toJsonSafe(Object value) {
        return convert(value, 0);
    }

    private static Object convert(Object value, int depth) {
        try {
            if (value == null) {
                return null;
            }
            if (value instanceof String || value instanceof Boolean || value instanceof Number) {
                return value; // already JSON-primitive
            }
            if (depth >= MAX_DEPTH) {
                return String.valueOf(value); // stop recursing; keep a readable form
            }
            if (value instanceof CharSequence || value instanceof Character) {
                return value.toString();
            }
            if (value instanceof Enum<?>) {
                return ((Enum<?>) value).name();
            }
            if (value instanceof Date) {
                return ((Date) value).toInstant().toString(); // ISO-8601
            }
            if (value instanceof java.util.Calendar) {
                return ((java.util.Calendar) value).getTime().toInstant().toString();
            }
            if (value instanceof byte[]) {
                return "<byte[" + ((byte[]) value).length + "]>";
            }
            if (value instanceof Map<?, ?>) {
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                    if (e.getKey() != null) {
                        out.put(String.valueOf(e.getKey()), convert(e.getValue(), depth + 1));
                    }
                }
                return out;
            }
            if (value instanceof Collection<?>) {
                List<Object> out = new ArrayList<Object>();
                for (Object el : (Collection<?>) value) {
                    out.add(convert(el, depth + 1));
                }
                return out;
            }
            if (value instanceof Object[]) {
                List<Object> out = new ArrayList<Object>();
                for (Object el : (Object[]) value) {
                    out.add(convert(el, depth + 1));
                }
                return out;
            }
            // Unknown/complex type (SailPoint object, proxy, custom bean): degrade to a safe string
            // rather than let Jackson attempt to introspect it and throw.
            return String.valueOf(value);
        } catch (Throwable t) {
            // A single unusual value must never fail the identity or the request.
            return "<unserializable:" + safeClassName(value) + ">";
        }
    }

    private static String safeClassName(Object value) {
        try {
            return value == null ? "null" : value.getClass().getName();
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
