package com.keyforge.nativeiiq.wire;

import sailpoint.tools.JsonHelper;
import sailpoint.tools.xml.XMLObjectFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Small read-only serializers used by the native mappers to render complex source objects into a single
 * faithful text value (so an added column can carry them without a new table). Never throws — a value it
 * cannot serialize degrades to null rather than failing the row. No renaming or inference.
 */
public final class NativeSerialize {

    private NativeSerialize() {
    }

    /** IdentityIQ's own canonical XML for a SailPoint object/graph, or null. Authoritative persisted form. */
    public static String xml(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return XMLObjectFactory.getInstance().toXml(o);
        } catch (Throwable t) {
            return null;
        }
    }

    /** A compact JSON array string of the given values (nulls dropped), or null if empty. */
    public static String jsonArray(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> clean = new ArrayList<String>();
        for (String v : values) {
            if (v != null) {
                clean.add(v);
            }
        }
        if (clean.isEmpty()) {
            return null;
        }
        try {
            return JsonHelper.toJson(clean);
        } catch (Throwable t) {
            return null;
        }
    }

    /** ISO-8601 instant text for a Date, or null. */
    public static String iso(Date d) {
        return d == null ? null : d.toInstant().toString();
    }

    /** Enum constant name, or null. */
    public static String enumName(Object e) {
        return (e instanceof Enum) ? ((Enum<?>) e).name() : (e == null ? null : String.valueOf(e));
    }
}
