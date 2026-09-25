package com.keyforge.nativeiiq.wire;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generic native introspector: it reads real zero-arg getters under their native bean-property names,
 * preserves nulls, renders scalars/lists/maps, exposes reference objects as {id,name,class}, and never
 * invents or renames anything. No SailPoint dependency, so it runs as a plain unit test.
 */
class ReflectivePropertiesTest {

    /** A reference-like object (has getId:String + getName) — stands in for a SailPointObject. */
    public static final class Ref {
        public String getId() { return "app-1"; }
        public String getName() { return "Active Directory"; }
    }

    /** A bean exercising scalar / boolean / null / list / map / reference / excluded members. */
    public static final class Bean {
        public String getValue() { return "CN=Admins"; }
        public String getDisplayableName() { return "Domain Admins"; }
        public boolean isRequestable() { return true; }
        public boolean isIiqElevatedAccess() { return false; }
        public String getDescription() { return null; }              // null preserved
        public List<String> getPermissions() { return new ArrayList<String>(); }
        public Map<String, Object> getAttributes() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("customKey", "customVal");
            return m;
        }
        public Ref getApplication() { return new Ref(); }            // reference -> {id,name,class}
        public String getURL() { return "https://x"; }               // acronym stays URL
        // --- must be EXCLUDED ---
        public void getVoid() { }                                    // void
        public String withArg(String a) { return a; }                // not a getter
        public String get(String k) { return k; }                    // has arg
        public static String getStaticThing() { return "no"; }       // static
    }

    private final Map<String, Object> props = ReflectiveProperties.of(new Bean());

    @Test
    void readsNativePropertyNamesFromAccessors() {
        assertTrue(props.containsKey("value"));
        assertTrue(props.containsKey("displayableName"));
        assertTrue(props.containsKey("requestable"));
        assertTrue(props.containsKey("iiqElevatedAccess"));
        assertEquals("CN=Admins", props.get("value"));
        assertEquals(Boolean.TRUE, props.get("requestable"));
        assertEquals(Boolean.FALSE, props.get("iiqElevatedAccess"));
    }

    @Test
    void preservesNulls() {
        assertTrue(props.containsKey("description"));
        assertNull(props.get("description"));
    }

    @Test
    void rendersCollectionsAndMaps() {
        assertTrue(props.get("permissions") instanceof List);
        assertTrue(props.get("attributes") instanceof Map);
        assertEquals("customVal", ((Map<?, ?>) props.get("attributes")).get("customKey"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rendersReferenceObjectAsIdNameClass() {
        Object app = props.get("application");
        assertTrue(app instanceof Map);
        Map<String, Object> ref = (Map<String, Object>) app;
        assertEquals("app-1", ref.get("id"));
        assertEquals("Active Directory", ref.get("name"));
        assertEquals(Ref.class.getName(), ref.get("class"));
    }

    @Test
    void acronymPropertyNameIsPreserved() {
        assertTrue(props.containsKey("URL"));
    }

    @Test
    void excludesVoidArgStaticAndGetClass() {
        assertFalse(props.containsKey("class"));
        assertFalse(props.containsKey("void"));
        assertFalse(props.containsKey("staticThing"));
        assertFalse(props.containsKey("")); // no empty-name property from get(String)
    }

    @Test
    void deterministicSortedOrdering() {
        List<String> keys = new ArrayList<String>(props.keySet());
        List<String> sorted = new ArrayList<String>(keys);
        java.util.Collections.sort(sorted);
        assertEquals(sorted, keys);
    }
}
