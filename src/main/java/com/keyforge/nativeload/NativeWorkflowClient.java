package com.keyforge.nativeload;
import com.keyforge.iiq.client.IiqSessionClient;import java.util.*;
public final class NativeWorkflowClient {public static final String PATH="plugin/rest/keyForgeNativeIIQ/workflows";private final IiqSessionClient session;private final String runId;public NativeWorkflowClient(IiqSessionClient s,String run){session=s;runId=run;}public String fetch(int start,int limit){session.warmCsrfToken();Map<String,String>q=new LinkedHashMap<>();q.put("start",String.valueOf(start));q.put("limit",String.valueOf(limit));q.put("runId",runId);return session.get(PATH,q);}}
