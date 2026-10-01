package com.keyforge.nativeiiq.model;

/**
 * A plain reference to one native {@code sailpoint.object.Permission} held by a ManagedAttribute
 * (direct or target permission). Pure data holder — no SailPoint dependency. Fields map to the 8.4
 * getters {@code getTarget()}, {@code getRights()}, {@code getAnnotation()}, {@code getAggregationSource()}.
 */
public final class NativePermissionRef {

    private final String target;
    private final String rights;
    private final String annotation;
    private final String aggregationSource;

    public NativePermissionRef(String target, String rights, String annotation, String aggregationSource) {
        this.target = target;
        this.rights = rights;
        this.annotation = annotation;
        this.aggregationSource = aggregationSource;
    }

    /** Backward-compatible overload (no aggregationSource) — used by the unchanged native Link path. */
    public NativePermissionRef(String target, String rights, String annotation) {
        this(target, rights, annotation, null);
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

    public String getAggregationSource() {
        return aggregationSource;
    }
}
