package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Small shared JSON-row readers + JDBC binders for the append-only Access-History repositories. */
final class NativeJsonRead {

    private NativeJsonRead() {
    }

    /** Scalar text (or null). Also used for source JSON strings stored verbatim in a text column. */
    static String text(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    /** A nested JSON object/array re-serialized to a string for a {@code jsonb} column (or null if absent/empty). */
    static String jsonString(JsonNode r, String field) {
        JsonNode n = r.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if ((n.isObject() || n.isArray()) && n.size() == 0) {
            return null; // empty map/list -> NULL rather than "{}"/"[]"
        }
        return n.toString();
    }

    static Boolean bool(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return (n == null || n.isNull()) ? null : Boolean.valueOf(n.asBoolean());
    }

    static Instant instant(JsonNode r, String field) {
        String s = text(r, field);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static void setBool(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value.booleanValue());
        }
    }

    static void setTs(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(index, value.atOffset(ZoneOffset.UTC));
        }
    }
}
