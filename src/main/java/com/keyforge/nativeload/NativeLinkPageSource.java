package com.keyforge.nativeload;

/**
 * Transport seam for the native Link (account) payload: fetches one page of the plugin endpoint's
 * JSON. Production impl is {@link NativeLinkClient}; tests supply canned pages.
 */
public interface NativeLinkPageSource {

    /** @return the raw JSON envelope for rows {@code start}..{@code start+limit}. */
    String fetchPage(int start, int limit);
}
