package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NativeRoleRelationshipImportServiceTest {
    private static String page(int count, int returned, String run, String edges) {
        return "{\"sourceCount\":"+count+",\"returnedBundles\":"+returned+",\"extractionRunId\":\""+run+"\",\"roleEntitlements\":[],\"roleHierarchy\":[]}";
    }
    private static final class Pages implements NativeRoleRelationshipPageSource {
        final List<String> responses; int calls;
        Pages(String... responses){this.responses=List.of(responses);}
        public String fetchPage(int start,int limit,String run){calls++;String r=responses.get(start/limit);return r.replace("RUN",run);}
    }
    private static final class Sink implements NativeRoleRelationshipSink {
        boolean ensured; int entitlementSweeps,hierarchySweeps; Collection<String> entitlementKeep,hierarchyKeep;
        boolean fail;
        public void ensure(){ensured=true;}
        public NativeRoleEntitlementRepository.Outcome entitlement(NativeRoleRelationshipRecord r)throws SQLException{if(fail)throw new SQLException("upsert failed");return NativeRoleEntitlementRepository.Outcome.INSERTED;}
        public NativeRoleHierarchyRepository.Outcome hierarchy(NativeRoleHierarchyRecord r){return NativeRoleHierarchyRepository.Outcome.UPDATED;}
        public SoftDeleteSweeper.SweepResult sweepEntitlements(Collection<String> ids){entitlementSweeps++;entitlementKeep=ids;return new SoftDeleteSweeper.SweepResult("kf_role_entitlement",ids.size(),0,0,ids.isEmpty(),null);}
        public SoftDeleteSweeper.SweepResult sweepHierarchy(Collection<String> ids){hierarchySweeps++;hierarchyKeep=ids;return new SoftDeleteSweeper.SweepResult("kf_role_hierarchy",ids.size(),0,0,ids.isEmpty(),null);}
    }

    @Test void confirmedFullScanAllowsSeparateTypedSweeps() throws SQLException {
        Pages pages=new Pages(page(3,2,"RUN",""),page(3,1,"RUN","")); Sink sink=new Sink();
        NativeRoleRelationshipImportService.Result result=new NativeRoleRelationshipImportService(pages,sink,2).run();
        assertTrue(sink.ensured);assertEquals(3,result.bundles);assertEquals(2,pages.calls);
        assertEquals(1,sink.entitlementSweeps);assertEquals(1,sink.hierarchySweeps);
        assertTrue(result.sweepsSkipped); // both source edge sets were genuinely empty
    }

    @Test void emptySourceReliesOnEmptyKeepSetSafetyGuard() throws SQLException {
        Pages pages=new Pages(page(0,0,"RUN","")); Sink sink=new Sink();
        NativeRoleRelationshipImportService.Result result=new NativeRoleRelationshipImportService(pages,sink,100).run();
        assertEquals(0,result.bundles);assertEquals(0,sink.entitlementKeep.size());assertTrue(result.sweepsSkipped);
    }

    @Test void rowFailureSkipsBothDeletionSweeps() throws SQLException {
        String row="{\"sourceBundleId\":\"bundle-1\",\"roleId\":\"bundle-1\",\"roleName\":\"R\",\"entitlementType\":\"PROFILE_CONSTRAINT\",\"srcNaturalKey\":\"key\"}";
        String response="{\"sourceCount\":1,\"returnedBundles\":1,\"extractionRunId\":\"RUN\",\"roleEntitlements\":["+row+"],\"roleHierarchy\":[]}";
        Pages pages=new Pages(response);Sink sink=new Sink();sink.fail=true;
        NativeRoleRelationshipImportService.Result result=new NativeRoleRelationshipImportService(pages,sink,10).run();
        assertEquals(1,result.failed);assertEquals(0,sink.entitlementSweeps);assertEquals(0,sink.hierarchySweeps);assertTrue(result.sweepsSkipped);
    }

    @Test void incompleteSourceScanFailsBeforeSweeps() {
        Pages pages=new Pages(page(4,2,"RUN",""),page(4,1,"RUN",""));Sink sink=new Sink();
        assertThrows(NativeImportException.class,()->new NativeRoleRelationshipImportService(pages,sink,2).run());
        assertEquals(0,sink.entitlementSweeps);assertEquals(0,sink.hierarchySweeps);
    }

    @Test void runIdMustRemainConsistentAcrossPages() {
        Pages pages=new Pages(page(2,1,"RUN",""),page(2,1,"another",""));Sink sink=new Sink();
        assertThrows(NativeImportException.class,()->new NativeRoleRelationshipImportService(pages,sink,1).run());
        assertEquals(0,sink.entitlementSweeps);
    }
}
