package com.keyforge.nativeiiq.model;

/**
 * A reference to one account (SailPoint {@code Link}) held by an Identity, as seen through the native
 * Java API. Pure data holder — no SailPoint dependency — so it is unit-testable without the IIQ
 * runtime. Carries the application name, the native (account) identity, the optional application
 * instance, and the account display name (all read-only via {@code Link} getters verified in 8.4).
 */
public final class NativeAccountRef {

    private final String applicationName;
    private final String nativeIdentity;
    private final String instance;
    private final String displayName;

    public NativeAccountRef(String applicationName, String nativeIdentity, String instance, String displayName) {
        this.applicationName = applicationName;
        this.nativeIdentity = nativeIdentity;
        this.instance = instance;
        this.displayName = displayName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public String getInstance() {
        return instance;
    }

    public String getDisplayName() {
        return displayName;
    }
}
