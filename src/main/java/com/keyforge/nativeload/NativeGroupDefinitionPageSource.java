package com.keyforge.nativeload;

/**
 * Transport seam for the native GroupDefinition payload: fetches one page of the plugin endpoint's
 * JSON. Production impl is {@link NativeGroupDefinitionClient}; tests supply canned pages.
 */
public interface NativeGroupDefinitionPageSource {

    /** @return the raw JSON envelope for rows {@code start}..{@code start+limit}. */
    String fetchPage(int start, int limit);
}
