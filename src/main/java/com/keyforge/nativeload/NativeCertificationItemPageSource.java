package com.keyforge.nativeload;

/** Transport seam for the native CertificationItem payload. Production impl: NativeCertificationItemClient. */
public interface NativeCertificationItemPageSource {

    String fetchPage(int start, int limit);
}
