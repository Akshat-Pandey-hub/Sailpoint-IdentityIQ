package com.keyforge.nativeload;

/** Transport seam for the native AuditEvent payload. Production impl: {@link NativeAuditEventClient}. */
public interface NativeAuditEventPageSource {

    String fetchPage(int start, int limit);
}
