
/**
 * Autogenerated by Jack
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 */
package com.liveramp.databases.workflow_db;

import com.rapleaf.jack.IDb;
import com.rapleaf.jack.queries.GenericQuery;
import com.liveramp.databases.workflow_db.iface.IApplicationConfiguredNotificationPersistence;
import com.liveramp.databases.workflow_db.iface.IApplicationCounterSummaryPersistence;
import com.liveramp.databases.workflow_db.iface.IApplicationPersistence;
import com.liveramp.databases.workflow_db.iface.IBackgroundAttemptInfoPersistence;
import com.liveramp.databases.workflow_db.iface.IBackgroundStepAttemptInfoPersistence;
import com.liveramp.databases.workflow_db.iface.IBackgroundWorkflowExecutorInfoPersistence;
import com.liveramp.databases.workflow_db.iface.IConfiguredNotificationPersistence;
import com.liveramp.databases.workflow_db.iface.IDashboardApplicationPersistence;
import com.liveramp.databases.workflow_db.iface.IDashboardPersistence;
import com.liveramp.databases.workflow_db.iface.IExecutionTagPersistence;
import com.liveramp.databases.workflow_db.iface.IMapreduceCounterPersistence;
import com.liveramp.databases.workflow_db.iface.IMapreduceJobTaskExceptionPersistence;
import com.liveramp.databases.workflow_db.iface.IMapreduceJobPersistence;
import com.liveramp.databases.workflow_db.iface.IResourceRecordPersistence;
import com.liveramp.databases.workflow_db.iface.IResourceRootPersistence;
import com.liveramp.databases.workflow_db.iface.IStepAttemptDatastorePersistence;
import com.liveramp.databases.workflow_db.iface.IStepAttemptPersistence;
import com.liveramp.databases.workflow_db.iface.IStepDependencyPersistence;
import com.liveramp.databases.workflow_db.iface.IStepStatisticPersistence;
import com.liveramp.databases.workflow_db.iface.IUserDashboardPersistence;
import com.liveramp.databases.workflow_db.iface.IUserPersistence;
import com.liveramp.databases.workflow_db.iface.IWorkflowAlertMapreduceJobPersistence;
import com.liveramp.databases.workflow_db.iface.IWorkflowAlertWorkflowExecutionPersistence;
import com.liveramp.databases.workflow_db.iface.IWorkflowAlertPersistence;
import com.liveramp.databases.workflow_db.iface.IWorkflowAttemptConfiguredNotificationPersistence;
import com.liveramp.databases.workflow_db.iface.IWorkflowAttemptDatastorePersistence;
import com.liveramp.databases.workflow_db.iface.IWorkflowAttemptPersistence;
import com.liveramp.databases.workflow_db.iface.IWorkflowExecutionConfiguredNotificationPersistence;
import com.liveramp.databases.workflow_db.iface.IWorkflowExecutionPersistence;
import com.liveramp.databases.workflow_db.IDatabases;

public interface IWorkflowDb extends IDb {
  IDatabases getDatabases();
  IApplicationConfiguredNotificationPersistence applicationConfiguredNotifications();
  IApplicationCounterSummaryPersistence applicationCounterSummaries();
  IApplicationPersistence applications();
  IBackgroundAttemptInfoPersistence backgroundAttemptInfos();
  IBackgroundStepAttemptInfoPersistence backgroundStepAttemptInfos();
  IBackgroundWorkflowExecutorInfoPersistence backgroundWorkflowExecutorInfos();
  IConfiguredNotificationPersistence configuredNotifications();
  IDashboardApplicationPersistence dashboardApplications();
  IDashboardPersistence dashboards();
  IExecutionTagPersistence executionTags();
  IMapreduceCounterPersistence mapreduceCounters();
  IMapreduceJobTaskExceptionPersistence mapreduceJobTaskExceptions();
  IMapreduceJobPersistence mapreduceJobs();
  IResourceRecordPersistence resourceRecords();
  IResourceRootPersistence resourceRoots();
  IStepAttemptDatastorePersistence stepAttemptDatastores();
  IStepAttemptPersistence stepAttempts();
  IStepDependencyPersistence stepDependencies();
  IStepStatisticPersistence stepStatistics();
  IUserDashboardPersistence userDashboards();
  IUserPersistence users();
  IWorkflowAlertMapreduceJobPersistence workflowAlertMapreduceJobs();
  IWorkflowAlertWorkflowExecutionPersistence workflowAlertWorkflowExecutions();
  IWorkflowAlertPersistence workflowAlerts();
  IWorkflowAttemptConfiguredNotificationPersistence workflowAttemptConfiguredNotifications();
  IWorkflowAttemptDatastorePersistence workflowAttemptDatastores();
  IWorkflowAttemptPersistence workflowAttempts();
  IWorkflowExecutionConfiguredNotificationPersistence workflowExecutionConfiguredNotifications();
  IWorkflowExecutionPersistence workflowExecutions();

}
