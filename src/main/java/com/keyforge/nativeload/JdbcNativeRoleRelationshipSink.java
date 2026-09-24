package com.keyforge.nativeload;
import com.keyforge.iiq.deletion.SoftDeleteSweeper;import java.sql.*;import java.util.Collection;
public final class JdbcNativeRoleRelationshipSink implements NativeRoleRelationshipSink{
 private final Connection c;private final NativeRoleEntitlementRepository e;private final NativeRoleHierarchyRepository h;
 public JdbcNativeRoleRelationshipSink(Connection c,NativeRoleEntitlementRepository e,NativeRoleHierarchyRepository h){this.c=c;this.e=e;this.h=h;}
 public void ensure()throws SQLException{e.ensure(c);h.ensure(c);}public NativeRoleEntitlementRepository.Outcome entitlement(NativeRoleRelationshipRecord r)throws SQLException{return e.upsert(c,r);}public NativeRoleHierarchyRepository.Outcome hierarchy(NativeRoleHierarchyRecord r)throws SQLException{return h.upsert(c,r);}public SoftDeleteSweeper.SweepResult sweepEntitlements(Collection<String>x)throws SQLException{return e.sweep(c,x);}public SoftDeleteSweeper.SweepResult sweepHierarchy(Collection<String>x)throws SQLException{return h.sweep(c,x);}
}
