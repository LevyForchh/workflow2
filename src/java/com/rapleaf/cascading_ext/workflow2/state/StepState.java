package com.rapleaf.cascading_ext.workflow2.state;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import com.rapleaf.cascading_ext.workflow2.Action;

public class StepState {

  //  immutable
  private final String actionClass;
  private final String stepId;

  //  required
  private StepStatus status;

  private String statusMessage;
  private String failureMessage;
  private String failureTrace;

  private long startTimestamp;
  private long endTimestamp;
  private final Set<String> stepDependencies;

  private final Multimap<Action.DSAction, DataStoreInfo> datastores;

  private Map<String, MapReduceJob> mrJobsByID = Maps.newHashMap();

  public StepState(String stepId,
                   StepStatus status,
                   String actionClass,
                   Set<String> dependencies,
                   Multimap<Action.DSAction, DataStoreInfo> datastores) {
    this.stepId = stepId;
    this.status = status;
    this.actionClass = actionClass;
    this.statusMessage = "";
    this.stepDependencies = dependencies;
    this.datastores = datastores;
  }

  protected StepState setStatus(StepStatus status) {
    this.status = status;
    return this;
  }

  protected StepState setStatusMessage(String statusMessage) {
    this.statusMessage = statusMessage;
    return this;
  }

  protected StepState setFailureMessage(String failureMessage) {
    this.failureMessage = failureMessage;
    return this;
  }

  protected StepState setFailureTrace(String failureTrace) {
    this.failureTrace = failureTrace;
    return this;
  }

  protected StepState setStartTimestamp(long startTimestamp) {
    this.startTimestamp = startTimestamp;
    return this;
  }

  protected StepState setEndTimestamp(long endTime) {
    this.endTimestamp = endTime;
    return this;
  }

  protected StepState addMrjob(MapReduceJob job) {
    this.mrJobsByID.put(job.getJobId(), job);
    return this;
  }

  public StepStatus getStatus() {
    return status;
  }

  public String getStepId() {
    return stepId;
  }

  public long getStartTimestamp() {
    return startTimestamp;
  }

  public long getEndTimestamp() {
    return endTimestamp;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public String getFailureTrace() {
    return failureTrace;
  }

  public String getStatusMessage() {
    return statusMessage;
  }

  public String getActionClass() {
    return actionClass;
  }

  public Map<String, MapReduceJob> getMrJobsByID() {
    return mrJobsByID;
  }

  public Set<String> getStepDependencies() {
    return stepDependencies;
  }

  public Multimap<Action.DSAction, DataStoreInfo> getDatastores() {
    return datastores;
  }
}
