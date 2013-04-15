package com.rapleaf.cascading_ext.workflow2;

import cascading.pipe.Pipe;
import cascading.tap.Tap;
import cascading.tuple.Fields;
import com.google.common.collect.Lists;
import com.rapleaf.cascading_ext.datastore.DataStore;

import java.util.List;
import java.util.Map;

public abstract class CheckpointedCascadingAction extends MultiStepAction {

  private EasyWorkflow workflowHelper;
  private String name;

  public CheckpointedCascadingAction(String checkpointToken, String workingDirectory, List<DataStore> inputs, List<DataStore> outputs) {
    super(checkpointToken);
    workflowHelper = new EasyWorkflow(this.getClass().getSimpleName(), workingDirectory);
    workflowHelper.setInputs(inputs);
    workflowHelper.setOutputs(outputs);

  }

  protected void complete(Pipe pipe, String pipeName) {
    setSubStepsFromTail(workflowHelper.completeAsStep(pipe, pipeName));
  }


  protected Pipe addCheckpoint(Pipe pipe, String pipeName, Fields fields, String checkpointName) {
    return workflowHelper.addCheckpoint(pipe, pipeName, fields, checkpointName);
  }

  protected Pipe addCheckpoint(Pipe pipe, String pipeName, String checkpointName) {
    return workflowHelper.addCheckpoint(pipe, pipeName, checkpointName);
  }

  protected Pipe addCheckpoint(Pipe pipe, String checkpointName) {
    return workflowHelper.addCheckpoint(pipe, checkpointName);
  }

  protected Pipe addCheckpoint(Pipe pipe) {
    return workflowHelper.addCheckpoint(pipe);
  }

  protected void addTail(Pipe tail) {
    workflowHelper.addTail(tail);
  }

  protected void addSourceTap(String name, Tap tap) {
    workflowHelper.addSourceTap(name, tap);
  }

  protected void addSinkTap(String name, Tap tap) {
    workflowHelper.addSinkTap(name, tap);
  }

  protected void addTails(List<Pipe> tails) {
    workflowHelper.addTails(tails);
  }

  protected void addTails(Pipe... tails) {
    workflowHelper.addTails(Lists.newArrayList(tails));
  }

  public void setName(String name) {
    this.name = name;
  }

  protected void addSourceTaps(Map<String, Tap> sources) {
    for (Map.Entry<String, Tap> source : sources.entrySet()) {
      addSourceTap(source.getKey(), source.getValue());
    }
  }

  protected void addSinkTap(Tap sink) {
    addSinkTap("singleton-sink", sink);
  }

  protected void addSinkTaps(Map<String, Tap> sinks) {
    for (Map.Entry<String, Tap> sink : sinks.entrySet()) {
      addSinkTap(sink.getKey(), sink.getValue());
    }
  }

  protected void addFlowProperties(Map<Object, Object> properties) {
    workflowHelper.addFlowProperties(properties);
  }

  protected void addSourceTap(Tap source) {
    addSourceTap("singleton-source", source);
  }
}
