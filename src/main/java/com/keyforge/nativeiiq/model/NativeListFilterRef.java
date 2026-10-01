package com.keyforge.nativeiiq.model;

/**
 * One native {@code sailpoint.service.listfilter.ListFilterValue} entry of an Application account filter
 * (service-account / RPA / disable / lock). Pure data holder — no SailPoint dependency. Fields map to the
 * 8.4 getters {@code getProperty()}, {@code getOperation()} (enum name), {@code getValue()} (JSON-safe) and
 * {@code getDisplayString()}. This is application-level CONFIGURATION that identifies accounts by attribute
 * criteria; it is NOT a per-account boolean. Values preserved exactly; a native null stays null.
 */
public final class NativeListFilterRef {

    private final String property;
    private final String operation;
    private final Object value;
    private final String displayString;

    public NativeListFilterRef(String property, String operation, Object value, String displayString) {
        this.property = property;
        this.operation = operation;
        this.value = value;
        this.displayString = displayString;
    }

    public String getProperty() { return property; }
    public String getOperation() { return operation; }
    public Object getValue() { return value; }
    public String getDisplayString() { return displayString; }
}
