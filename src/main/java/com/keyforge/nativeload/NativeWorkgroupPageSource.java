package com.keyforge.nativeload;

/**
 * Transport seam for the native Workgroup payload: fetches one page of the plugin endpoint's JSON.
 * Production impl is {@link NativeWorkgroupClient}; tests supply canned pages.
 */
public interface NativeWorkgroupPageSource {

    /** @return the raw JSON envelope for rows {@code start}..{@code start+limit}. */
    String fetchPage(int start, int limit);
}
