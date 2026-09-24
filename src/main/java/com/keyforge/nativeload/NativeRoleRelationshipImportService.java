package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Pages by source Bundle count and only sweeps current-state edges after a confirmed complete scan. */
public final class NativeRoleRelationshipImportService {
    public static final class Result {
        public final String extractionRunId;
        public final int sourceCount, bundles, entitlements, entitlementInserted, entitlementUpdated;
        public final int hierarchy, hierarchyInserted, hierarchyUpdated, failed, entitlementDeleted, hierarchyDeleted;
        public final boolean sweepsSkipped;
        Result(String run, int sc, int b, int e, int ei, int eu, int h, int hi, int hu, int f, int ed, int hd, boolean ss) {
            extractionRunId=run; sourceCount=sc; bundles=b; entitlements=e; entitlementInserted=ei; entitlementUpdated=eu;
            hierarchy=h; hierarchyInserted=hi; hierarchyUpdated=hu; failed=f; entitlementDeleted=ed; hierarchyDeleted=hd; sweepsSkipped=ss;
        }
    }
    private final NativeRoleRelationshipPageSource source;
    private final NativeRoleRelationshipSink sink;
    private final int pageSize;
    private final NativeRoleRelationshipParser parser = new NativeRoleRelationshipParser();

    public NativeRoleRelationshipImportService(NativeRoleRelationshipPageSource source, NativeRoleRelationshipSink sink, int pageSize) {
        this.source=source; this.sink=sink; this.pageSize=pageSize>0?pageSize:100;
    }

    public Result run() throws SQLException {
        sink.ensure();
        String runId=UUID.randomUUID().toString();
        int start=0, sourceCount=-1, bundles=0, e=0, ei=0, eu=0, h=0, hi=0, hu=0, failed=0;
        Set<String> keepEntitlements=new LinkedHashSet<String>(), keepHierarchy=new LinkedHashSet<String>();
        while (true) {
            NativeRoleRelationshipPage page=parser.parse(source.fetchPage(start,pageSize,runId));
            if (!runId.equals(page.runId)) throw new NativeImportException("Role relationship page run ID mismatch; no deletion sweep performed");
            if (page.sourceCount<0 || page.returnedBundles<0) throw new NativeImportException("Native role relationship response has invalid scan counts");
            if (sourceCount>=0 && sourceCount!=page.sourceCount) throw new NativeImportException("Bundle source count changed during scan; no deletion sweep performed");
            sourceCount=page.sourceCount;
            bundles+=page.returnedBundles;
            for (NativeRoleRelationshipRecord r:page.entitlements) {
                e++;
                try { String id=NativeRoleEntitlementRepository.id(r); NativeRoleEntitlementRepository.Outcome o=sink.entitlement(r); keepEntitlements.add(id); if(o==NativeRoleEntitlementRepository.Outcome.INSERTED)ei++;else eu++; }
                catch (Exception ex) { failed++; }
            }
            for (NativeRoleHierarchyRecord r:page.hierarchy) {
                h++;
                try { String id=NativeRoleHierarchyRepository.id(r); NativeRoleHierarchyRepository.Outcome o=sink.hierarchy(r); keepHierarchy.add(id); if(o==NativeRoleHierarchyRepository.Outcome.INSERTED)hi++;else hu++; }
                catch (Exception ex) { failed++; }
            }
            if (page.returnedBundles<pageSize) break;
            start+=pageSize;
        }
        if (bundles!=sourceCount) throw new NativeImportException("Incomplete Bundle relationship scan: IIQ count="+sourceCount+", scanned="+bundles+"; deletion sweeps not run");
        if (failed>0) return new Result(runId,sourceCount,bundles,e,ei,eu,h,hi,hu,failed,0,0,true);
        SoftDeleteSweeper.SweepResult se=sink.sweepEntitlements(keepEntitlements), sh=sink.sweepHierarchy(keepHierarchy);
        return new Result(runId,sourceCount,bundles,e,ei,eu,h,hi,hu,0,se.marked(),sh.marked(),se.skipped()||sh.skipped());
    }
}
