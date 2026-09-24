package com.keyforge.nativeiiq.service;
import com.keyforge.nativeiiq.config.NativeExtractionConfig;import com.keyforge.nativeiiq.model.NativeRoleRelationshipExtractionResult;import com.keyforge.nativeiiq.source.NativeRoleRelationshipExtractor;import sailpoint.api.SailPointContext;import sailpoint.tools.GeneralException;
/** Transport-independent, read-only native Bundle relationship extraction. */
public final class NativeRoleRelationshipExtractionService {
 public NativeRoleRelationshipExtractionResult extract(SailPointContext c,int start,int limit,String runId)throws GeneralException{return new NativeRoleRelationshipExtractor(c,NativeExtractionConfig.of(null,limit,0,null,runId)).extract(Math.max(0,start),limit);}
}
