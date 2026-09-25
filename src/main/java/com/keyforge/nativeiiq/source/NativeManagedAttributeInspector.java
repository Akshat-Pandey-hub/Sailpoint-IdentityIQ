package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.wire.JsonSafe;
import com.keyforge.nativeiiq.wire.ReflectiveProperties;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLObjectFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * SOURCE-TRUTH inspector for the native {@code sailpoint.object.ManagedAttribute} (entitlement) object.
 * Read-only; uses ONLY the native SailPoint Java API ({@link SailPointContext} search / getObjectById /
 * decache) — no SCIM, no REST, and none of the KeyForge {@code kf_entitlement} mapper/schema/terminology.
 *
 * <p>For each object it captures three complementary, un-renamed views so the real source model is visible:
 * <ul>
 *   <li>{@code _native_getters} — every public zero-arg native accessor and its value
 *       ({@link ReflectiveProperties}), keyed by the native bean-property name;</li>
 *   <li>{@code _native_attributes} — the raw {@code getAttributes()} map (custom/extended attributes),
 *       original keys preserved exactly;</li>
 *   <li>{@code _native_xml} — IdentityIQ's own canonical serialization
 *       ({@code XMLObjectFactory.toXml}) with the exact persisted property names (the authoritative
 *       source representation).</li>
 * </ul>
 * Nothing is invented or inferred; nulls are preserved as null.
 */
public final class NativeManagedAttributeInspector {

    public static final String OBJECT_TYPE = "sailpoint.object.ManagedAttribute";

    private final boolean includeXml;

    public NativeManagedAttributeInspector(boolean includeXml) {
        this.includeXml = includeXml;
    }

    /**
     * Inspects a deterministic window of ManagedAttribute objects and returns a JSON-friendly envelope
     * (Maps/Lists/Strings/Numbers/Booleans/null only).
     *
     * @param context the live IIQ context (supplied by the runtime)
     * @param start   first row (0-based); &le;0 means from the beginning
     * @param limit   max rows; &le;0 means no limit (full population)
     */
    public Map<String, Object> inspect(SailPointContext context, int start, int limit) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("id", true); // deterministic; a ManagedAttribute name may be null
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }

        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        // property name -> {kind, populated, null, total} aggregated across the population
        Map<String, int[]> counts = new TreeMap<String, int[]>();   // [total, populated, null]
        Map<String, String> kinds = new TreeMap<String, String>();
        TreeMap<String, Integer> extendedKeys = new TreeMap<String, Integer>();

        Iterator<Object[]> ids = context.search(ManagedAttribute.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            ManagedAttribute ma = context.getObjectById(ManagedAttribute.class, id);
            if (ma == null) {
                continue;
            }
            try {
                Map<String, Object> getters = ReflectiveProperties.of(ma);   // native property names
                Map<String, Object> attrs = safeAttributes(ma.getAttributes());

                for (Map.Entry<String, Object> e : getters.entrySet()) {
                    int[] c = counts.get(e.getKey());
                    if (c == null) {
                        c = new int[3];
                        counts.put(e.getKey(), c);
                    }
                    c[0]++;
                    if (isPopulated(e.getValue())) {
                        c[1]++;
                    } else {
                        c[2]++;
                    }
                    if (!kinds.containsKey(e.getKey()) || "null".equals(kinds.get(e.getKey()))) {
                        kinds.put(e.getKey(), kindOf(e.getValue()));
                    }
                }
                for (String k : attrs.keySet()) {
                    Integer n = extendedKeys.get(k);
                    extendedKeys.put(k, Integer.valueOf(n == null ? 1 : n.intValue() + 1));
                }

                Map<String, Object> rec = new LinkedHashMap<String, Object>();
                rec.put("_native_id", ma.getId());
                rec.put("_native_getters", getters);
                rec.put("_native_attributes", attrs);
                if (includeXml) {
                    rec.put("_native_xml", toXml(ma));
                }
                records.add(rec);
            } finally {
                context.decache(ma);
            }
        }

        Map<String, Object> propertySummary = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            int[] c = e.getValue();
            Map<String, Object> s = new LinkedHashMap<String, Object>();
            s.put("kind", kinds.get(e.getKey()));
            s.put("total", Integer.valueOf(c[0]));
            s.put("populated", Integer.valueOf(c[1]));
            s.put("null_or_empty", Integer.valueOf(c[2]));
            propertySummary.put(e.getKey(), s);
        }

        Map<String, Object> envelope = new LinkedHashMap<String, Object>();
        envelope.put("object_type", OBJECT_TYPE);
        envelope.put("source", "native SailPointContext Java API (no SCIM, no REST, no kf_entitlement)");
        envelope.put("generated_at", Instant.now().toString());
        envelope.put("start", Integer.valueOf(Math.max(0, start)));
        envelope.put("limit", Integer.valueOf(limit));
        envelope.put("count", Integer.valueOf(records.size()));
        envelope.put("native_getter_property_summary", propertySummary);
        envelope.put("native_extended_attribute_keys", new ArrayList<String>(extendedKeys.keySet()));
        envelope.put("records", records);
        return envelope;
    }

    private String toXml(ManagedAttribute ma) {
        try {
            return XMLObjectFactory.getInstance().toXml(ma);
        } catch (Throwable t) {
            return "<unserializable-xml:" + t.getClass().getSimpleName() + ">";
        }
    }

    private static Map<String, Object> safeAttributes(Attributes<String, Object> attrs) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (attrs == null) {
            return out;
        }
        for (Object key : attrs.keySet()) {
            if (key == null) {
                continue;
            }
            out.put(key.toString(), JsonSafe.toJsonSafe(attrs.get(key.toString()))); // original key preserved
        }
        return out;
    }

    private static boolean isPopulated(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof CharSequence) {
            return ((CharSequence) v).length() > 0;
        }
        if (v instanceof java.util.Collection) {
            return !((java.util.Collection<?>) v).isEmpty();
        }
        if (v instanceof java.util.Map) {
            return !((java.util.Map<?, ?>) v).isEmpty();
        }
        return true;
    }

    private static String kindOf(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            return (m.containsKey("id") && m.containsKey("class")) ? "reference" : "map";
        }
        if (v instanceof java.util.Collection) {
            return "list";
        }
        if (v instanceof String || v instanceof Number || v instanceof Boolean) {
            return "scalar";
        }
        return "object";
    }
}
