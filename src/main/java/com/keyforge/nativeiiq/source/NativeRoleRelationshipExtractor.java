package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeRoleRelationshipMapper;
import com.keyforge.nativeiiq.model.NativeRoleRelationshipExtractionResult;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import java.util.Iterator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded, deterministic, read-only Bundle scan for role-entitlement/profile and role-hierarchy edges. */
public final class NativeRoleRelationshipExtractor {
    private final SailPointContext context; private final NativeExtractionConfig config;
    public NativeRoleRelationshipExtractor(SailPointContext c,NativeExtractionConfig x){context=c;config=x;}
    public NativeRoleRelationshipExtractionResult extract(int start,int limit)throws GeneralException{
        return extract(start, limit, null);
    }
    public NativeRoleRelationshipExtractionResult extract(int start,int limit,Date modifiedAfter)throws GeneralException{
        return extract(start, limit, modifiedAfter, true);
    }
    /** One page; the Bundle population is counted only when {@code includeCount} is true ({@code -1} otherwise). */
    public NativeRoleRelationshipExtractionResult extract(int start,int limit,Date modifiedAfter,boolean includeCount)throws GeneralException{
        Set<String> changedBundleIds = changedProfileParents(modifiedAfter);
        QueryOptions qo=new QueryOptions();qo.addOrdering("id",true);addIncrementalFilter(qo, modifiedAfter, changedBundleIds);if(start>0)qo.setFirstRow(start);if(limit>0)qo.setResultLimit(limit);
        NativeRoleRelationshipExtractionResult out=new NativeRoleRelationshipExtractionResult();
        QueryOptions countOptions = new QueryOptions();addIncrementalFilter(countOptions, modifiedAfter, changedBundleIds);
        if (includeCount) {
            out.setSourceCount(context.countObjects(Bundle.class,countOptions));
        }
        Iterator<Object[]> ids=context.search(Bundle.class,qo,"id");
        try {
while(ids!=null&&ids.hasNext()){String id=(String)ids.next()[0];Bundle b=context.getObjectById(Bundle.class,id);if(b==null)continue;
            try{out.setReturnedBundles(out.getReturnedBundles()+1);NativeRoleRelationshipMapper.map(b,out,config.getSourceSystem(),config.getExtractionRunId());}
            finally{context.decache(b);}} } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }
        return out;
    }

    private Set<String> changedProfileParents(Date modifiedAfter) throws GeneralException {
        Set<String> ids = new LinkedHashSet<String>();
        if (modifiedAfter == null) return ids;
        QueryOptions profiles = new QueryOptions();
        NativeIncrementalQuery.applyModifiedAfter(profiles, modifiedAfter);
        Iterator<Profile> changed = context.search(Profile.class, profiles);
        try {
            while (changed != null && changed.hasNext()) {
                Profile profile = changed.next();
                if (profile == null) continue;
                try {
                    Bundle bundle = profile.getBundle();
                    if (bundle != null && bundle.getId() != null) {
                        ids.add(bundle.getId());
                        if (NativeIncrementalQuery.exceedsChangedParentIdLimit(ids)) return null;
                    }
                } finally {
                    context.decache(profile);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(changed);
        }
        return ids;
    }

    private static void addIncrementalFilter(QueryOptions options, Date modifiedAfter, Set<String> changedBundleIds) {
        if (modifiedAfter == null) return;
        if (changedBundleIds == null) return; // too many changed children: safely fall back to all parents
        Filter parentChanged = NativeIncrementalQuery.changedAfterFilter(modifiedAfter);
        if (changedBundleIds == null || changedBundleIds.isEmpty()) options.addFilter(parentChanged);
        else options.addFilter(Filter.or(parentChanged, Filter.in("id", changedBundleIds)));
    }

}
