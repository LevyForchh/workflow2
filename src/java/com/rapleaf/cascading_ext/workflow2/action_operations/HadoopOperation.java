package com.rapleaf.cascading_ext.workflow2.action_operations;

import com.google.common.collect.Maps;
import com.liveramp.cascading_ext.counters.Counter;
import com.rapleaf.cascading_ext.counters.NestedCounter;
import com.rapleaf.cascading_ext.workflow2.ActionOperation;
import com.rapleaf.cascading_ext.workflow2.RunnableJob;
import com.rapleaf.cascading_ext.workflow2.Step;
import com.rapleaf.support.event_timer.FixedTimedEvent;
import org.apache.hadoop.mapred.Counters;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class HadoopOperation implements ActionOperation {
  private static Logger LOG = Logger.getLogger(HadoopOperation.class);

  public static final String SINGLE_JOB_NAME = "Job 1/1";
  private final RunnableJob runnableJob;

  private JobConf conf = null;
  private RunningJob runningJob = null;
  private Long startTime = null;
  private Long finishTime = null;
  private JobClient jobClient = null;

  public HadoopOperation(RunnableJob job) {
    this.runnableJob = job;
  }

  @Override
  public void start() {
    try {
      markStarted();
      conf = runnableJob.configure();
      jobClient = new JobClient(conf);
      runningJob = jobClient.submitJob(conf);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void complete() {
    try {
      runningJob.waitForCompletion();
      LOG.info(com.liveramp.cascading_ext.counters.Counters.prettyCountersString(runningJob));
      if (!runningJob.isSuccessful()) {
        throw new RuntimeException("Job " + getName() + " failed!: " + runningJob.getFailureInfo());
      }
      runnableJob.complete(runningJob);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      markFinished();
    }
  }

  @Override
  public String getProperty(String propertyName) {
    return conf.get(propertyName);
  }

  @Override
  public int getProgress(int maxPct) throws IOException {
    double isComplete = runningJob.isComplete() ? 1.0 : 0.0;
    return (int) isComplete * maxPct;
  }

  @Override
  public String getName() {
    return conf.getJobName();
  }

  @Override
  public Map<String, String> getSubStepIdToName(int operationIndex) {
    Map<String, String> subStepIdToName = Maps.newHashMap();

    try {
      subStepIdToName.put(runningJob.getID().toString(), SINGLE_JOB_NAME);
    } catch (NullPointerException npe) {
      // getID on occasion throws a null pointer exception, ignore it
    }

    return subStepIdToName;
  }

  @Override
  public void timeOperation(Step.StepTimer stepTimer, String checkpointToken, List<NestedCounter> nestedCounters) {
    Counters counterGroups;

    try {
      counterGroups = runningJob.getCounters();
    } catch (NullPointerException e) {
      counterGroups = new Counters();
    } catch (IOException e) {
      counterGroups = new Counters();
    }

    for (Counters.Group counterGroup : counterGroups) {
      final String groupName = counterGroup.getName();

      for (Counters.Counter c : counterGroup) {
        if (c.getValue() > 0) {
          Counter singleValueCounter = new Counter(groupName, c.getName(), c.getValue());
          nestedCounters.add(new NestedCounter(singleValueCounter, checkpointToken));
        }
      }
    }

    if (runningJob != null) {
      stepTimer.addChild(new FixedTimedEvent(runningJob.getJobName(), startTime, finishTime));
    }
  }

  private void markStarted() {
    startTime = System.currentTimeMillis();
  }

  private void markFinished() {
    finishTime = System.currentTimeMillis();
  }
}
