package com.keyforge.nativeload;

/**
 * Transport seam for the native account-entitlement payload: fetches one page (a window of {@code Link}s)
 * of the plugin endpoint's JSON. Production impl is {@link NativeAccountEntitlementClient}; tests supply
 * canned pages.
 */
public interface NativeAccountEntitlementPageSource {

    /** @return the raw JSON envelope for Links {@code start}..{@code start+limit} and their edges. */
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
