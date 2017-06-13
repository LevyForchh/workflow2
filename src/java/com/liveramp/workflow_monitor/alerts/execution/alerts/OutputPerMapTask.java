package com.liveramp.workflow_monitor.alerts.execution.alerts;

import java.text.DecimalFormat;

import com.google.common.collect.Multimap;

import com.liveramp.commons.collections.map.MultimapBuilder;
import com.liveramp.commons.collections.nested_map.TwoNestedMap;
import com.liveramp.databases.workflow_db.models.MapreduceJob;
import com.liveramp.java_support.ByteUnit;
import com.liveramp.workflow_monitor.alerts.execution.JobThresholdAlert;
import com.liveramp.workflow_monitor.alerts.execution.thresholds.GreaterThan;
import com.liveramp.workflow_state.WorkflowRunnerNotification;

public class OutputPerMapTask extends JobThresholdAlert {


  static String SHORT_DESCRIPTION = "Map tasks output over 5GB post-serialization. ";
  static String RECOMMENDATION = "Reading spills which are this large can cause machine performance problems; please increase the number of " +
      "map tasks this data is spread over.";


  protected static final Multimap<String, String> REQUIRED_COUNTERS = new MultimapBuilder<String, String>()
      .put(TASK_COUNTER_GROUP, MAP_OUTPUT_MATERIALIZED_BYTES)
      .put(JOB_COUNTER_GROUP, LAUNCHED_MAPS)
      .put(JOB_COUNTER_GROUP, LAUNCHED_REDUCES)
      .get();

  private static final long MAX_OUTPUT_THRESHOLD = ByteUnit.GIGABYTES.toBytes(5);

  public OutputPerMapTask() {
    super(MAX_OUTPUT_THRESHOLD, WorkflowRunnerNotification.PERFORMANCE, REQUIRED_COUNTERS, new GreaterThan());
  }

  @Override
  protected Double calculateStatistic(MapreduceJob job, TwoNestedMap<String, String, Long> counters) {

    Long materializedBytes = counters.get(TASK_COUNTER_GROUP, MAP_OUTPUT_MATERIALIZED_BYTES);
    Long launchedMaps = counters.get(JOB_COUNTER_GROUP, LAUNCHED_MAPS);
    Long launchedReduces = counters.get(JOB_COUNTER_GROUP, LAUNCHED_REDUCES);

    if (launchedMaps != null && launchedReduces != null && materializedBytes != null) {
      return materializedBytes.doubleValue() / launchedMaps.doubleValue();
    }

    return null;
  }

  private static final DecimalFormat df = new DecimalFormat("##.##");

  @Override
  protected String getMessage(double value) {
    return "Map tasks in this job are outputting on average " +
        df.format(value / ((double)ByteUnit.GIGABYTES.toBytes(1))) + "GB post-serialization. " + RECOMMENDATION;

  }
}
