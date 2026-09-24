package com.keyforge.nativeload;

/** Transport seam for the native WorkItem payload. Production impl: {@link NativeWorkItemClient}. */
public interface NativeWorkItemPageSource {

    String fetchPage(int start, int limit);
}
