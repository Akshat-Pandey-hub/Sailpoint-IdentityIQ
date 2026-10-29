package com.keyforge.nativeload;

/** Transport seam for the native CertificationEntity payload. Production impl: {@link NativeCertificationEntityClient}. */
public interface NativeCertificationEntityPageSource {

    String fetchPage(int start, int limit);

    /**
     * One page, asking the plugin to count the whole (bounded) population only when {@code includeCount} is true.
     * Counting reads every source row, so only a full read asks, once, for its completeness check; a page read
     * never does.
     */
    default String fetchPage(int start, int limit, boolean includeCount) {
        return fetchPage(start, limit);
    }
}
