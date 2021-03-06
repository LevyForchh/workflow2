
/**
 * Autogenerated by Jack
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 */
package com.liveramp.databases.workflow_db.iface;

import com.liveramp.databases.workflow_db.models.WorkflowAlertMapreduceJob;
import com.liveramp.databases.workflow_db.query.WorkflowAlertMapreduceJobQueryBuilder;
import com.liveramp.databases.workflow_db.query.WorkflowAlertMapreduceJobDeleteBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.List;

import com.rapleaf.jack.IModelPersistence;

public interface IWorkflowAlertMapreduceJobPersistence extends IModelPersistence<WorkflowAlertMapreduceJob> {
  WorkflowAlertMapreduceJob create(final long workflow_alert_id, final long mapreduce_job_id) throws IOException;

  WorkflowAlertMapreduceJob createDefaultInstance() throws IOException;
  List<WorkflowAlertMapreduceJob> findByWorkflowAlertId(long value)  throws IOException;
  List<WorkflowAlertMapreduceJob> findByMapreduceJobId(long value)  throws IOException;

  WorkflowAlertMapreduceJobQueryBuilder query();

  WorkflowAlertMapreduceJobDeleteBuilder delete();
}
