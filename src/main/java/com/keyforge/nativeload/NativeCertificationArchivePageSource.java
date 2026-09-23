package com.keyforge.nativeload;

/**
 * Transport seam for the native CertificationArchive payload: fetches one page of the plugin endpoint's
 * JSON. Production impl is {@link NativeCertificationArchiveClient}; tests supply canned pages.
 */
public interface NativeCertificationArchivePageSource {

    /** @return the raw JSON envelope for rows {@code start}..{@code start+limit}. */
    String fetchPage(int start, int limit);
}
