package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class NativeRoleRelationshipRepositoryTest {
    private static NativeRoleRelationshipRecord record(String naturalKey,String run,String extracted) {
        NativeRoleRelationshipRecord r=new NativeRoleRelationshipRecord();r.sourceBundleId="00000000000000000000000000000001";
        r.roleId=r.sourceBundleId;r.roleName="Engineer";r.entitlementType="PROFILE_CONSTRAINT";r.srcNaturalKey=naturalKey;
        r.application="Directory";r.attributeName="department";r.attributeValue="Engineering";r.filterOperation="EQ";
        r.extractionRunId=run;r.extractedAt=java.time.Instant.parse(extracted);return r;
    }
    @Test void edgeIdIsStableForSameSourceNaturalKeyAndDistinctForOtherEdges(){
        assertEquals(NativeRoleEntitlementRepository.id(record("edge-a","run-1","2026-01-01T00:00:00Z")),NativeRoleEntitlementRepository.id(record("edge-a","run-2","2026-01-02T00:00:00Z")));
        assertNotEquals(NativeRoleEntitlementRepository.id(record("edge-a","run-1","2026-01-01T00:00:00Z")),NativeRoleEntitlementRepository.id(record("edge-b","run-1","2026-01-01T00:00:00Z")));
    }
    @Test void businessHashExcludesRunLineage(){
        NativeRoleRelationshipRecord a=record("edge-a","run-1","2026-01-01T00:00:00Z"),b=record("edge-a","run-2","2026-01-02T00:00:00Z");
        assertEquals(NativeRoleEntitlementRepository.hash(a,null),NativeRoleEntitlementRepository.hash(b,null));
        NativeRoleHierarchyRecord h1=new NativeRoleHierarchyRecord(),h2=new NativeRoleHierarchyRecord();
        h1.sourceRoleId=h2.sourceRoleId="a";h1.relatedRoleId=h2.relatedRoleId="b";h1.relationshipType="INHERITANCE";h2.relationshipType="PERMIT";
        assertNotEquals(NativeRoleHierarchyRepository.hash(h1),NativeRoleHierarchyRepository.hash(h2));
    }

    @Test void entitlementRepositoryUsesConflictUpsertAndOnlyUniqueMatchLookup() throws Exception {
        AtomicReference<String> insertedSql=new AtomicReference<>();
        Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement")){
                String sql=(String)args[0];
                if(sql.startsWith("INSERT"))insertedSql.set(sql);
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
                    if(m.getName().equals("executeQuery")){
                        boolean insert=sql.startsWith("INSERT");
                        return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},new java.lang.reflect.InvocationHandler(){boolean read;
                            public Object invoke(Object rp,java.lang.reflect.Method rm,Object[] ra){if(rm.getName().equals("next")){if(insert&&!read){read=true;return true;}return false;}if(rm.getName().equals("getBoolean"))return true;return defaultValue(rm.getReturnType());}});
                    }
                    return defaultValue(m.getReturnType());
                });
            }
            return defaultValue(method.getReturnType());
        });
        NativeRoleEntitlementRepository repo=new NativeRoleEntitlementRepository("iiq_native");
        assertEquals(NativeRoleEntitlementRepository.Outcome.INSERTED,repo.upsert(connection,record("edge-a","run-1","2026-01-01T00:00:00Z")));
        assertTrue(insertedSql.get().contains("ON CONFLICT(roleentitlementid) DO UPDATE"));
        assertEquals("iiq_native.kf_role_entitlement",repo.targetTable());
    }

    @Test void hierarchyRepositoryUsesIdempotentUpsert(){
        AtomicReference<String> sqlSeen=new AtomicReference<>();
        Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement")){
                sqlSeen.set((String)args[0]);
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
                    if(m.getName().equals("executeQuery"))return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},new java.lang.reflect.InvocationHandler(){boolean read;
                        public Object invoke(Object rp,java.lang.reflect.Method rm,Object[] ra){if(rm.getName().equals("next")){if(!read){read=true;return true;}return false;}if(rm.getName().equals("getBoolean"))return true;return defaultValue(rm.getReturnType());}});
                    return defaultValue(m.getReturnType());
                });
            }
            return defaultValue(method.getReturnType());
        });
        NativeRoleHierarchyRepository repo=new NativeRoleHierarchyRepository("iiq_native");NativeRoleHierarchyRecord row=new NativeRoleHierarchyRecord();
        row.sourceRoleId="parent";row.relatedRoleId="child";row.relationshipType="PERMIT";row.srcNaturalKey="edge";
        assertDoesNotThrow(()->assertEquals(NativeRoleHierarchyRepository.Outcome.INSERTED,repo.upsert(connection,row)));
        assertTrue(sqlSeen.get().contains("ON CONFLICT(rolehierarchyid) DO UPDATE"));assertEquals("iiq_native.kf_role_hierarchy",repo.targetTable());
    }

    private static Object defaultValue(Class<?> type){
        if(!type.isPrimitive())return null;if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;
        if(type==double.class)return 0d;if(type==float.class)return 0f;if(type==short.class)return (short)0;if(type==byte.class)return (byte)0;if(type==char.class)return '\0';return null;
    }
}
