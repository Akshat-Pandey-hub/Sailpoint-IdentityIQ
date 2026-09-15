package com.keyforge.iiq.catalog;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.model.Entitlement;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Derives requestable-entitlement catalog rows. Pure and DB-free.
 *
 * <p>Catalog identity is <b>(name, appinstanceid)</b> (the target's unique
 * constraint), NOT per-entitlement. Entitlements sharing a catalog identity collapse
 * to ONE row: the {@code aggregated=false} entitlement is chosen as PRIMARY and its
 * fields populate the columns, while EVERY contributing entitlement's complete payload
 * is preserved in {@code customattributes}. Nothing is lost. {@code description},
 * {@code entitlementowner}, and {@code status} are always NULL (verified absent from
 * the source) — never invented.
 */
public final class CatalogRowMapper {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /** Every catalog row this service produces is an Entitlement catalog item. */
    public static final String CATALOG_TYPE_ENTITLEMENT = "Entitlement";

    private CatalogRowMapper() {
    }

    /**
     * Groups entitlements by catalog identity and produces exactly one {@link CatalogRow}
     * per identity.
     *
     * @param entitlements           all requestable entitlement candidates
     * @param existingEntitlementIds canonical entitlementids in &lt;schema&gt;.kf_entitlement
     * @param existingInstanceIds    canonical instanceids in &lt;schema&gt;.applicationinstance
     */
    public static List<CatalogRow> deriveCatalogRows(List<Entitlement> entitlements,
                                                     Set<String> existingEntitlementIds,
                                                     Set<String> existingInstanceIds) {
        // Group by the deterministic catalog id so grouping and the PK are always consistent.
        Map<String, List<Entitlement>> groups = new LinkedHashMap<>();
        for (Entitlement e : entitlements) {
            String catalogId = deterministicCatalogId(catalogName(e), canonicalAppInstanceId(e));
            groups.computeIfAbsent(catalogId, k -> new ArrayList<>()).add(e);
        }

        List<CatalogRow> rows = new ArrayList<>(groups.size());
        for (List<Entitlement> group : groups.values()) {
            rows.add(mapGroup(group, existingEntitlementIds, existingInstanceIds));
        }
        return rows;
    }

    private static CatalogRow mapGroup(List<Entitlement> group,
                                       Set<String> existingEntitlementIds,
                                       Set<String> existingInstanceIds) {
        Entitlement primary = selectPrimary(group);

        String name = catalogName(primary);
        String canonicalAppId = canonicalAppInstanceId(primary);
        String catalogid = deterministicCatalogId(name, canonicalAppId);

        String entitlementid = resolveId(primary.getId(), existingEntitlementIds);
        String appinstanceid = canonicalAppId != null && existingInstanceIds != null
                && existingInstanceIds.contains(canonicalAppId) ? canonicalAppId : null;

        Entitlement.ApplicationRef app = primary.getApplication();
        String applicationName = app == null ? null : blankToNull(app.getDisplayName());

        Entitlement.Meta meta = primary.getMeta();
        LocalDateTime created = meta == null ? null : parseScimTimestamp(meta.getCreated());
        LocalDateTime lastModified = meta == null ? null : parseScimTimestamp(meta.getLastModified());

        return new CatalogRow(
                catalogid,
                name,
                CATALOG_TYPE_ENTITLEMENT,
                entitlementid,
                blankToNull(primary.getValue()),      // entitlement_name <- primary value
                blankToNull(primary.getType()),       // entitlement_type = raw source string
                primary.getRequestable(),
                applicationName,
                appinstanceid,
                created,
                lastModified,
                buildSourceEntitlements(primary, group));
    }

    /**
     * Explicit, deterministic primary selection: {@code aggregated=false} first
     * (the native/requestable representation), then the lowest entitlement id as a
     * stable tiebreak. Never arbitrary/random.
     */
    static Entitlement selectPrimary(List<Entitlement> group) {
        return group.stream()
                .min(Comparator.comparingInt((Entitlement e) -> isAggregated(e) ? 1 : 0)
                        .thenComparing(e -> nullToEmpty(e.getId())))
                .orElseThrow(() -> new CatalogMappingException("empty catalog group"));
    }

    static boolean isAggregated(Entitlement e) {
        return e.getAdditionalAttributes().path("aggregated").asBoolean(false);
    }

    /** Catalog name = displayableName when present, else the entitlement value. */
    static String catalogName(Entitlement e) {
        String displayName = blankToNull(e.getDisplayableName());
        return displayName != null ? displayName : blankToNull(e.getValue());
    }

    /** Canonical UUID of the entitlement's application (for identity + column), or null. */
    static String canonicalAppInstanceId(Entitlement e) {
        Entitlement.ApplicationRef app = e.getApplication();
        if (app == null || app.getValue() == null || app.getValue().isBlank()) {
            return null;
        }
        try {
            return toCanonicalUuid(app.getValue());
        } catch (CatalogMappingException notAUuid) {
            return null;
        }
    }

    /**
     * Deterministic catalog id from the catalog IDENTITY (name + appinstanceid), NOT
     * from entitlementid — so duplicate entitlement representations collapse to the same
     * row and re-runs are idempotent.
     */
    public static String deterministicCatalogId(String name, String canonicalAppInstanceId) {
        String key = CATALOG_TYPE_ENTITLEMENT + "|" + nullToEmpty(name) + "|" + nullToEmpty(canonicalAppInstanceId);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** Canonical id iff it exists in the migration set, else null (never fabricated). */
    public static String resolveId(String rawId, Set<String> existingIds) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        String canonical;
        try {
            canonical = toCanonicalUuid(rawId);
        } catch (CatalogMappingException notAUuid) {
            return null;
        }
        return existingIds != null && existingIds.contains(canonical) ? canonical : null;
    }

    /**
     * Derivation provenance: which entitlement(s) produced this catalog item — the
     * PRIMARY plus any ADDITIONAL representations that collapsed into it, each with its
     * id/value/type. This documents the derivation (genuinely useful) rather than
     * dumping the raw resource.
     */
    static String buildSourceEntitlements(Entitlement primary, List<Entitlement> group) {
        ObjectNode root = JSON.objectNode();
        root.put("catalogSource", CATALOG_TYPE_ENTITLEMENT);
        if (primary.getId() != null) {
            root.put("primaryEntitlementId", primary.getId());
        }

        // Primary first, then the additional entitlements in a deterministic order.
        List<Entitlement> ordered = new ArrayList<>();
        ordered.add(primary);
        group.stream()
                .filter(m -> m != primary)
                .sorted(Comparator.comparing(m -> nullToEmpty(m.getId())))
                .forEach(ordered::add);

        ArrayNode sources = root.putArray("sourceEntitlements");
        for (Entitlement m : ordered) {
            ObjectNode node = sources.addObject();
            if (m.getId() != null) {
                node.put("entitlementId", m.getId());
            }
            node.put("role", m == primary ? "PRIMARY" : "ADDITIONAL");
            // Compact provenance — enough to explain the derivation, not a raw dump.
            putIfPresent(node, "value", m.getValue());
            putIfPresent(node, "displayableName", m.getDisplayableName());
            putIfPresent(node, "type", m.getType());
        }
        return root.toString();
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    // --- shared helpers -----------------------------------------------------

    static String toCanonicalUuid(String rawId) {
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
            throw new CatalogMappingException("'" + rawId + "' is not a valid PostgreSQL UUID");
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

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
