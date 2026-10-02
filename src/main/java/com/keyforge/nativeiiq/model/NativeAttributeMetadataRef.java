package com.keyforge.nativeiiq.model;

import java.time.Instant;

/**
 * A reference to one native {@code sailpoint.object.AttributeMetaData} entry on a Link (account) — the
 * per-attribute source provenance IIQ records for a tracked account attribute: which attribute, which
 * source/feed last set it, which user, when, and the prior value. This is source-authoritative
 * provenance (who/what owns each attribute value) not represented by the flat attribute map. Pure data
 * holder (no SailPoint dependency). Fields map to the 8.4 getters {@code getAttribute()},
 * {@code getSource()}, {@code getUser()}, {@code getModified()}, {@code getLastValue()}.
 *
 * <p>{@code lastValue} is made JSON-safe by the mapper and redacted when the attribute is a
 * schema-declared secret, so a secret's prior value is never persisted in clear.
 */
public final class NativeAttributeMetadataRef {

    private final String attribute;
    private final String source;
    private final String user;
    private final Instant modified;
    private final Object lastValue;

    public NativeAttributeMetadataRef(String attribute, String source, String user, Instant modified,
                                      Object lastValue) {
        this.attribute = attribute;
        this.source = source;
        this.user = user;
        this.modified = modified;
        this.lastValue = lastValue;
    }

    public String getAttribute() { return attribute; }
    public String getSource() { return source; }
    public String getUser() { return user; }
    public Instant getModified() { return modified; }
    public Object getLastValue() { return lastValue; }
}
