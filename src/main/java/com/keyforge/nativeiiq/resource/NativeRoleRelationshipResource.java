package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.keyforge.nativeiiq.model.NativeRoleRelationshipExtractionResult;import com.keyforge.nativeiiq.service.NativeRoleRelationshipExtractionService;import com.keyforge.nativeiiq.wire.NativeRoleRelationshipWire;import sailpoint.api.SailPointContext;import sailpoint.authorization.UnauthorizedAccessException;import sailpoint.rest.plugin.BasePluginResource;import sailpoint.rest.plugin.SystemAdmin;import javax.ws.rs.*;import javax.ws.rs.core.*;import java.util.*;
/** Read-only Bundle profile and hierarchy relationship export. */
@Path("keyForgeNativeIIQ") public class NativeRoleRelationshipResource extends BasePluginResource{
    private static final Log LOG = LogFactory.getLog(NativeRoleRelationshipResource.class);

 @Override public String getPluginName(){return "KeyForgeNativeIIQ";}
 @GET @Path("role-relationships") @SystemAdmin @Produces(MediaType.APPLICATION_JSON)
 public Response get(@QueryParam("start") @DefaultValue("0") int start,@QueryParam("limit") @DefaultValue("100") int limit,@QueryParam("runId") String runId,@QueryParam("modifiedAfter") String modifiedAfter,@QueryParam("includeCount") @DefaultValue("false") boolean includeCount){
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "role-relationships", start, limit, modifiedAfter);if(com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)){PluginLog.rejected(LOG, "role-relationships", "invalid modifiedAfter timestamp");return error(400,com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());}java.util.Date changedAfter=com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);String stage="context";try{int s=Math.max(0,start),l=limit<=0?100:Math.min(1000,limit);String rid=runId==null||runId.trim().isEmpty()?java.util.UUID.randomUUID().toString():runId.trim();SailPointContext c=getContext();stage="extract";NativeRoleRelationshipExtractionResult r=new NativeRoleRelationshipExtractionService().extract(c,s,l,rid,changedAfter,includeCount);if(LOG.isDebugEnabled())LOG.debug("KeyForgeNativeIIQ role-relationships parentCount="+r.getSourceCount()+", entitlementRows="+r.getRoleEntitlements().size()+", hierarchyRows="+r.getRoleHierarchy().size());
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=role-relationships count=" + r.getSourceCount());
            }PluginLog.result(LOG, "role-relationships", r.getReturnedBundles(), requestStartedAt);
            return Response.ok(NativeRoleRelationshipWire.envelope(r,s,l,rid)).build();}catch(UnauthorizedAccessException e){PluginLog.denied(LOG, "role-relationships", stage, e);return error(403,e);}catch(Throwable t){PluginLog.failed(LOG, "role-relationships", stage, t);return error(500,t);}}
 private static Response error(int status,Throwable t){Map<String,Object>e=new LinkedHashMap<>();e.put("type",t.getClass().getName());e.put("message",String.valueOf(t.getMessage()));Map<String,Object>m=new LinkedHashMap<>();m.put("entity","BundleRelationships");m.put("status",status);m.put("error",e);return Response.status(status).type(MediaType.APPLICATION_JSON).entity(m).build();}
}
