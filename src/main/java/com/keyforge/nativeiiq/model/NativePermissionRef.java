package com.keyforge.nativeiiq.model;

/**
 * A plain reference to one native {@code sailpoint.object.Permission} held by a ManagedAttribute
 * (direct or target permission). Pure data holder — no SailPoint dependency. Fields map to the 8.4
 * getters {@code getTarget()}, {@code getRights()}, {@code getAnnotation()}.
 */
public final class NativePermissionRef {

    private final String target;
    private final String rights;
    private final String annotation;

    public NativePermissionRef(String target, String rights, String annotation) {
        this.target = target;
        this.rights = rights;
        this.annotation = annotation;
    }

    public String getTarget() {
        return target;
    }

    public String getRights() {
        return rights;
    }

    public String getAnnotation() {
        return annotation;
    }
}
