package com.keyforge.nativeiiq.model;

/**
 * A minimal {id, name} reference to another native SailPoint object (e.g. an inherited
 * {@code ManagedAttribute}). Pure data holder — no SailPoint dependency.
 */
public final class NativeReferenceRef {

    private final String id;
    private final String name;

    public NativeReferenceRef(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
