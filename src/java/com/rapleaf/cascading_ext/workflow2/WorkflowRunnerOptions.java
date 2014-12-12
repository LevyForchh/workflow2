package com.rapleaf.cascading_ext.workflow2;

import com.liveramp.java_support.alerts_handler.NoOpAlertsHandler;
import com.rapleaf.cascading_ext.workflow2.options.WorkflowOptions;

import com.rapleaf.cascading_ext.workflow2.registry.ZkRegistry;
import com.rapleaf.cascading_ext.workflow2.stats.RecorderFactory;
import com.rapleaf.support.Rap;

//  TODO this should get renamed ProductionWorkflowOptions at some point.  Goal is that
//  this is instantiated in only production
public class WorkflowRunnerOptions extends WorkflowOptions<WorkflowRunnerOptions> {

  public WorkflowRunnerOptions() {
    Rap.assertProduction();

    setMaxConcurrentSteps(Integer.MAX_VALUE);
    setAlertsHandler(new NoOpAlertsHandler());
    setEnabledNotifications(WorkflowRunnerNotificationSet.all());
    setStatsRecorder(new RecorderFactory.StatsD());
    setLockProvider(null);
    setStorage(new ContextStorage.None());
    setRegistry(new ZkRegistry());
  }

}
