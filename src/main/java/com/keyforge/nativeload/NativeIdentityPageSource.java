package com.keyforge.nativeload;

/**
 * Transport seam for the native Identity payload: fetches one page of the plugin endpoint's JSON.
 * The production implementation ({@link NativeIdentityClient}) pulls over the authenticated IIQ web
 * session; keeping it behind an interface lets {@link NativeIdentityImportService} be unit-tested with
 * canned pages and no live server — and keeps the orchestration independent of the HTTP transport.
 */
public interface NativeIdentityPageSource {

    /** @return the raw JSON envelope for rows {@code start}..{@code start+limit}. */
    String fetchPage(int start, int limit);
}
