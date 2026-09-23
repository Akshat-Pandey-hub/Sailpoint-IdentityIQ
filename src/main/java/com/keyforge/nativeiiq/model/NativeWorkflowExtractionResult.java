package com.keyforge.nativeiiq.model;
import java.time.Instant; import java.util.ArrayList; import java.util.List;
public final class NativeWorkflowExtractionResult {
 private final String sourceSystem, extractionRunId; private final List<NativeWorkflowRow> workflows=new ArrayList<NativeWorkflowRow>(); private final Instant startedAt; private Instant finishedAt;
 public NativeWorkflowExtractionResult(String s,String r,Instant t){sourceSystem=s;extractionRunId=r;startedAt=t;}
 public String getSourceSystem(){return sourceSystem;} public String getExtractionRunId(){return extractionRunId;} public List<NativeWorkflowRow> getWorkflows(){return workflows;} public Instant getStartedAt(){return startedAt;} public Instant getFinishedAt(){return finishedAt;} public void setFinishedAt(Instant v){finishedAt=v;} public int getCount(){return workflows.size();}
}
