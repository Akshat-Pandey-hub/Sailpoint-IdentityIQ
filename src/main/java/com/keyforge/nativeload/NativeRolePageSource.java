package com.keyforge.nativeload;

/**
 * Transport seam for the native Role (Bundle) payload: fetches one page of the plugin endpoint's JSON.
 * Production impl is {@link NativeRoleClient}; tests supply canned pages.
 */
public interface NativeRolePageSource {

    /** @return the raw JSON envelope for rows {@code start}..{@code start+limit}. */
    String fetchPage(int start, int limit);
}
