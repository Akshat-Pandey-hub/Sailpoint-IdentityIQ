package com.keyforge.nativeload;

/** Transport seam for the native CertificationEntity payload. Production impl: {@link NativeCertificationEntityClient}. */
public interface NativeCertificationEntityPageSource {

    String fetchPage(int start, int limit);
}
