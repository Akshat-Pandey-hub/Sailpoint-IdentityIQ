package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeRoleRelationshipMapper;
import com.keyforge.nativeiiq.model.NativeRoleRelationshipExtractionResult;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import java.util.Iterator;

/** Bounded, deterministic, read-only Bundle scan for role-entitlement/profile and role-hierarchy edges. */
public final class NativeRoleRelationshipExtractor {
    private final SailPointContext context; private final NativeExtractionConfig config;
    public NativeRoleRelationshipExtractor(SailPointContext c,NativeExtractionConfig x){context=c;config=x;}
    public NativeRoleRelationshipExtractionResult extract(int start,int limit)throws GeneralException{
        QueryOptions qo=new QueryOptions();qo.addOrdering("id",true);if(start>0)qo.setFirstRow(start);if(limit>0)qo.setResultLimit(limit);
        NativeRoleRelationshipExtractionResult out=new NativeRoleRelationshipExtractionResult();
        out.setSourceCount(context.countObjects(Bundle.class,new QueryOptions()));
        Iterator<Object[]> ids=context.search(Bundle.class,qo,"id");
        while(ids!=null&&ids.hasNext()){String id=(String)ids.next()[0];Bundle b=context.getObjectById(Bundle.class,id);if(b==null)continue;
            try{out.setReturnedBundles(out.getReturnedBundles()+1);NativeRoleRelationshipMapper.map(b,out,config.getSourceSystem(),config.getExtractionRunId());}
            finally{context.decache(b);}}
        return out;
    }
}
