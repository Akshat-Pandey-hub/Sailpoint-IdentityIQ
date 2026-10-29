package com.keyforge.nativeload;
/** Fetches one Bundle page carrying both Stack B relationship collections. */
public interface NativeRoleRelationshipPageSource {
    String fetchPage(int start,int limit,String runId);

    /** One page; asks the plugin to count the Bundle population only when {@code includeCount} is true. */
    default String fetchPage(int start,int limit,String runId,boolean includeCount){ return fetchPage(start,limit,runId); }
}
