package com.keyforge.nativeiiq.wire;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generic, read-only JavaBean property introspector used ONLY by the source-inspection command. It reads
 * every public, zero-argument, non-static {@code getX()}/{@code isX()} accessor actually present on the
 * target object and returns a name&rarr;value map keyed by the <b>native</b> bean-property name derived
 * directly from the accessor (e.g. {@code getDisplayableName} &rarr; {@code displayableName},
 * {@code isRequestable} &rarr; {@code requestable}). It performs <b>no renaming into KeyForge terms</b>,
 * invents no fields, and never throws — a single unreadable accessor becomes a small marker string rather
 * than failing the record.
 *
 * <p>Values are made JSON-safe via {@link JsonSafe}. A value that is itself a reference object (any object
 * exposing zero-arg {@code getId()}:String and {@code getName()}) is rendered as {@code {id, name, class}}
 * so a native reference (application, owner, &hellip;) is visible without flattening or guessing. This
 * class has no SailPoint dependency so it is unit-testable on a plain bean.
 *
 * <p>Output ordering is deterministic (sorted property names) so two runs can be diffed.
 */
public final class ReflectiveProperties {

    private ReflectiveProperties() {
    }

    /** @return sorted native-property &rarr; JSON-safe value map for {@code target} (never null, never throws). */
    public static Map<String, Object> of(Object target) {
        Map<String, Object> out = new TreeMap<String, Object>();
        if (target == null) {
            return out;
        }
        for (Method m : target.getClass().getMethods()) {
            String prop = propertyName(m);
            if (prop == null) {
                continue;
            }
            Object value;
            try {
                Object raw = m.invoke(target);
                value = render(raw);
            } catch (Throwable t) {
                value = "<unreadable:" + t.getClass().getSimpleName() + ">";
            }
            out.put(prop, value);
        }
        return out;
    }

    /** The native bean-property name for a readable accessor, or null if {@code m} is not one. */
    static String propertyName(Method m) {
        if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) {
            return null;
        }
        if (m.getReturnType() == void.class) {
            return null;
        }
        String n = m.getName();
        if ("getClass".equals(n)) {
            return null;
        }
        if (n.startsWith("get") && n.length() > 3) {
            return decapitalize(n.substring(3));
        }
        if (n.startsWith("is") && n.length() > 2
                && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
            return decapitalize(n.substring(2));
        }
        return null;
    }

    /** JavaBean decapitalization (leaves all-caps acronyms like {@code URL} untouched). */
    static String decapitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        if (s.length() > 1 && Character.isUpperCase(s.charAt(0)) && Character.isUpperCase(s.charAt(1))) {
            return s;
        }
        char[] c = s.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }

    private static Object render(Object raw) {
        Map<String, Object> ref = asReference(raw);
        return ref != null ? ref : JsonSafe.toJsonSafe(raw);
    }

    /** If {@code v} looks like a reference object (getId:String + getName), render {id,name,class}. */
    static Map<String, Object> asReference(Object v) {
        if (v == null || v instanceof CharSequence || v instanceof Number || v instanceof Boolean
                || v instanceof java.util.Map || v instanceof java.util.Collection || v instanceof Object[]) {
            return null;
        }
        try {
            Method getId = v.getClass().getMethod("getId");
            Method getName = v.getClass().getMethod("getName");
            if (getId.getReturnType() != String.class) {
                return null;
            }
            Map<String, Object> ref = new LinkedHashMap<String, Object>();
            ref.put("id", getId.invoke(v));
            ref.put("name", getName.invoke(v));
            ref.put("class", v.getClass().getName());
            return ref;
        } catch (Throwable ignore) {
            return null;
        }
    }
}
