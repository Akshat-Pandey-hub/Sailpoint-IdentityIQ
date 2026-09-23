package com.keyforge.nativeload;

/** Transport seam for the native Certification payload. Production impl: {@link NativeCertificationClient}. */
public interface NativeCertificationPageSource {

    String fetchPage(int start, int limit);
}
