package com.keyforge.nativeload;

/**
 * Transport seam for the native TaskResult payload: fetches one page of the plugin endpoint's JSON.
 * Production impl is {@link NativeTaskResultClient}; tests supply canned pages.
 */
public interface NativeTaskResultPageSource {

    /** @return the raw JSON envelope for rows {@code start}..{@code start+limit}. */
    String fetchPage(int start, int limit);
}
