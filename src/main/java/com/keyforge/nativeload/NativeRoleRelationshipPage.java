package com.keyforge.nativeload;
import java.util.List;
final class NativeRoleRelationshipPage{
 final int sourceCount,returnedBundles;final String runId;final List<NativeRoleRelationshipRecord> entitlements;final List<NativeRoleHierarchyRecord> hierarchy;
 NativeRoleRelationshipPage(int c,int b,String r,List<NativeRoleRelationshipRecord> e,List<NativeRoleHierarchyRecord> h){sourceCount=c;returnedBundles=b;runId=r;entitlements=e;hierarchy=h;}
}
