package com.keyforge.nativeload;

/**
 * Transport seam for the native TaskSchedule payload: fetches one page of the plugin endpoint's JSON.
 * Production impl is {@link NativeTaskScheduleClient}; tests supply canned pages.
 */
public interface NativeTaskSchedulePageSource {

    /** @return the raw JSON envelope for rows {@code start}..{@code start+limit}. */
    String fetchPage(int start, int limit);
}
