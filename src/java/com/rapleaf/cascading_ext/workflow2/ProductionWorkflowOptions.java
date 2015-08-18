package com.rapleaf.cascading_ext.workflow2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.liveramp.cascading_ext.megadesk.MockStoreReaderLockProvider;
import com.liveramp.java_support.alerts_handler.LoggingAlertsHandler;
import com.rapleaf.cascading_ext.workflow2.counter.CounterFilters;
import com.rapleaf.cascading_ext.workflow2.options.WorkflowOptions;
import com.rapleaf.support.Rap;

public class ProductionWorkflowOptions extends WorkflowOptions {
  private static final Logger LOG = LoggerFactory.getLogger(ProductionWorkflowOptions.class);

  private static final String WORKFLOW_UI_URL = "http://workflows.liveramp.net";

  public ProductionWorkflowOptions() {

    Rap.assertProduction();

    setMaxConcurrentSteps(Integer.MAX_VALUE);
    setAlertsHandler(new LoggingAlertsHandler());
    setEnabledNotifications(WorkflowRunnerNotificationSet.all());
    setLockProvider(new MockStoreReaderLockProvider());
    setStorage(new ContextStorage.None());
    setStepPollInterval(3000);  // be nice to production DB
    setCounterFilter(CounterFilters.all());
    setUrlBuilder(new DbTrackerURLBuilder(WORKFLOW_UI_URL));

  }
}
