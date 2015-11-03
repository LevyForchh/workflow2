package com.liveramp.workflow_monitor.alerts.execution;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import com.liveramp.workflow_monitor.alerts.execution.alert.AlertMessage;
import com.rapleaf.db_schemas.rldb.models.WorkflowAttempt;
import com.rapleaf.db_schemas.rldb.models.WorkflowExecution;

public interface ExecutionAlertGenerator {
  public List<AlertMessage> generateAlerts(WorkflowExecution execution, Collection<WorkflowAttempt> attempts) throws IOException;
}
