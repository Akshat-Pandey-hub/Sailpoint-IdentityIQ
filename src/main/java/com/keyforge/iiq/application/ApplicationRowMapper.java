package com.keyforge.iiq.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.model.Application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Maps an IdentityIQ {@link Application} to an {@link ApplicationRow}. Pure and DB-free.
 * Every Application field goes to its own typed column; schemas/features/descriptions
 * are preserved as JSON arrays. Nothing is invented or dropped.
 */
public final class ApplicationRowMapper {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private ApplicationRowMapper() {
    }

    public static ApplicationRow map(Application application) {
        String applicationid = toCanonicalUuid(application.getId());

        Application.Owner owner = application.getOwner();
        String ownerId = owner == null ? null : canonicalOrNull(owner.getValue());
        String ownerDisplayName = owner == null ? null : blankToNull(owner.getDisplayName());

        Application.Meta meta = application.getMeta();
        LocalDateTime created = meta == null ? null : parseScimTimestamp(meta.getCreated());
        LocalDateTime modified = meta == null ? null : parseScimTimestamp(meta.getLastModified());

        return new ApplicationRow(
                applicationid,
                blankToNull(application.getName()),
                blankToNull(application.getType()),
                ownerId,
                ownerDisplayName,
                schemasJson(application),
                featuresJson(application),
                jsonArrayOrNull(application.getAdditionalAttributes().get("descriptions")),
                created,
                modified);
    }

    /** The application schema references as a JSON array [{type,value,$ref}], or null. */
    static String schemasJson(Application application) {
        if (application.getApplicationSchemas().isEmpty()) {
            return null;
        }
        ArrayNode arr = JSON.arrayNode();
        for (Application.ApplicationSchema s : application.getApplicationSchemas()) {
            ObjectNode node = JSON.objectNode();
            putIfPresent(node, "type", s.getType());
            putIfPresent(node, "value", s.getValue());
            putIfPresent(node, "$ref", s.getRef());
            arr.add(node);
        }
        return arr.isEmpty() ? null : arr.toString();
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static String jsonArrayOrNull(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty() ? node.toString() : null;
    }

    /**
     * The {@code features} array as a proper JSONB array, preserving every real value.
     * IdentityIQ's SCIM serializer sometimes emits a Java array's default
     * {@code toString()} (e.g. {@code "[Ljava.lang.String;@626cb311"}) instead of the
     * real feature strings; those artifact elements are excluded because they are not
     * source data (they carry no recoverable value). Genuine strings are never touched;
     * if nothing real remains the column is left NULL. Never applies {@code toString()}
     * to the array itself.
     */
    static String featuresJson(Application application) {
        JsonNode node = application.getAdditionalAttributes().get("features");
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        ArrayNode cleaned = JSON.arrayNode();
        for (JsonNode element : node) {
            if (element.isTextual() && isJavaArrayArtifact(element.asText())) {
                continue; // IIQ serialization artifact, not a real feature value
            }
            cleaned.add(element);
        }
        return cleaned.isEmpty() ? null : cleaned.toString();
    }

    /** True for Java's default array {@code toString()}, e.g. {@code [Ljava.lang.String;@1a2b3c}. */
    static boolean isJavaArrayArtifact(String s) {
        return s != null && s.matches("\\[+([A-Z]|L[\\w.$]+;)@[0-9a-fA-F]+");
    }

    /** Canonical UUID or null (never throws) — for reference values that may be absent/odd. */
    static String canonicalOrNull(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return toCanonicalUuid(rawId);
        } catch (ApplicationMappingException notAUuid) {
            return null;
        }
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new ApplicationMappingException(
                    "IdentityIQ application id is missing; cannot use as application.applicationid (UUID).");
        }
        String s = rawId.trim();
        if (s.startsWith("{") && s.endsWith("}") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        String hex = s.replace("-", "");
        if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
            String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
            return UUID.fromString(dashed).toString();
        }
        try {
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException e) {
            throw new ApplicationMappingException(
                    "IdentityIQ application id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    static LocalDateTime parseScimTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException withOffset) {
            try {
                return LocalDateTime.parse(value.trim());
            } catch (DateTimeParseException withoutOffset) {
                return null;
            }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
