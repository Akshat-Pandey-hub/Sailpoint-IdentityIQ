package com.keyforge.nativeload;

/**
 * Transport seam for the native ManagedAttribute payload: fetches one page of the plugin endpoint's
 * JSON. Production impl is {@link NativeManagedAttributeClient}; tests supply canned pages.
 */
public interface NativeManagedAttributePageSource {

    /** @return the raw JSON envelope for rows {@code start}..{@code start+limit}. */
    String fetchPage(int start, int limit);
}
