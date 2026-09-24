package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRoleRelationshipParserTest {
    private final NativeRoleRelationshipParser parser=new NativeRoleRelationshipParser();
    @Test void parsesObjectEnvelopeAndStructuredFilterValue(){
        NativeRoleRelationshipPage p=parser.parse("{\"sourceCount\":1,\"returnedBundles\":1,\"extractionRunId\":\"r\",\"roleEntitlements\":[{\"sourceBundleId\":\"b\",\"entitlementType\":\"PROFILE_CONSTRAINT\",\"filterValue\":[\"x\",2],\"srcNaturalKey\":\"n\"}],\"roleHierarchy\":[{\"relationshipType\":\"PERMIT\"}]}");
        assertEquals("r",p.runId);assertEquals(1,p.entitlements.size());assertTrue(p.entitlements.get(0).filterValue instanceof java.util.List);assertEquals("PERMIT",p.hierarchy.get(0).relationshipType);
    }
    @Test void genuineEmptyArraysAreValid(){assertEquals(0,parser.parse("{\"sourceCount\":0,\"returnedBundles\":0,\"extractionRunId\":\"r\",\"roleEntitlements\":[],\"roleHierarchy\":[]}").returnedBundles);}
    @Test void rejectsStringMalformedAndErrorResponses(){assertThrows(NativeImportException.class,()->parser.parse("\"{}\""));assertThrows(NativeImportException.class,()->parser.parse("{oops"));assertThrows(NativeImportException.class,()->parser.parse("{\"error\":{\"type\":\"Denied\",\"message\":\"no\"}}"));}
}
