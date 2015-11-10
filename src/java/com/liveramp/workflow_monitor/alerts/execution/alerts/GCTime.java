package com.liveramp.workflow_monitor.alerts.execution.alerts;

import com.liveramp.commons.collections.map.MultimapBuilder;
import com.liveramp.commons.collections.nested_map.TwoNestedMap;
import com.liveramp.workflow_monitor.alerts.execution.JobThresholdAlert;
import com.rapleaf.db_schemas.rldb.workflow.WorkflowRunnerNotification;

public class GCTime extends JobThresholdAlert {

  public static final double GC_FRACTION_THRESHOLD = .2;

  private static final String GC_TIME_MILLIS = "GC_TIME_MILLIS";
  private static final String MILLIS_MAPS = "MILLIS_MAPS";
  private static final String MILLIS_REDUCES = "MILLIS_REDUCES";

  public GCTime() {
    super(GC_FRACTION_THRESHOLD, WorkflowRunnerNotification.PERFORMANCE, new MultimapBuilder<String, String>()
        .put(TASK_COUNTER_GROUP, GC_TIME_MILLIS)
        .put(JOB_COUNTER_GROUP, MILLIS_MAPS)
        .put(JOB_COUNTER_GROUP, MILLIS_REDUCES).get());
  }

  @Override
  protected Double calculateStatistic(TwoNestedMap<String, String, Long> counters) {

    Long gcTime = counters.get(TASK_COUNTER_GROUP, GC_TIME_MILLIS);
    Long milliMaps = counters.get(JOB_COUNTER_GROUP, MILLIS_MAPS);
    Long milliReduces = counters.get(JOB_COUNTER_GROUP, MILLIS_REDUCES);

    if (gcTime == null) {
      return null;
    }

    Long allTime = 0L;

    if (milliMaps != null) {
      allTime += milliMaps;
    }

    if (milliReduces != null) {
      allTime += milliReduces;
    }

    return gcTime.doubleValue() / allTime.doubleValue();
  }

  @Override
  protected String getMessage(double value) {
    return (value * 100) + "% of processing time was spent in Garbage Collection.  " +
        "This can be triggered by excessive object creation or insufficient heap size.  " +
        "Try to reduce object instantiations or increase task heap sizes.";
  }
}
