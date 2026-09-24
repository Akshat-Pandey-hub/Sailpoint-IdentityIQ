package com.keyforge.nativeload;
import com.keyforge.iiq.client.IiqSessionClient;import java.util.LinkedHashMap;import java.util.Map;
/** Authenticated native Bundle relationship endpoint client. */
public final class NativeRoleRelationshipClient implements NativeRoleRelationshipPageSource{
 public static final String PATH="plugin/rest/keyForgeNativeIIQ/role-relationships";private final IiqSessionClient session;
 public NativeRoleRelationshipClient(IiqSessionClient s){session=s;}
 public String fetchPage(int start,int limit,String runId){session.warmCsrfToken();Map<String,String>p=new LinkedHashMap<>();p.put("start",Integer.toString(Math.max(0,start)));p.put("limit",Integer.toString(limit));p.put("runId",runId);return session.get(PATH,p);}
}
