package com.liveramp.workflow_db_state.controller;

import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.liveramp.workflow_db_state.WorkflowDbStateTestCase;
import org.junit.Test;

import com.liveramp.databases.workflow_db.DatabasesImpl;
import com.liveramp.databases.workflow_db.IWorkflowDb;
import com.liveramp.databases.workflow_db.models.Application;
import com.liveramp.databases.workflow_db.models.ConfiguredNotification;
import com.liveramp.workflow_db_state.WorkflowQueries;
import com.liveramp.workflow_state.WorkflowRunnerNotification;

public class TestApplicationController extends WorkflowDbStateTestCase {

  @Test
  public void testNotificaions() throws Exception {

    IWorkflowDb rlDb = new DatabasesImpl().getWorkflowDb();
    Application test = rlDb.applications().create("test");

    ApplicationController.addConfiguredNotifications(rlDb, "test", "ben@gmail.com",
        Sets.newHashSet(WorkflowRunnerNotification.DIED_UNCLEAN, WorkflowRunnerNotification.FAILURE)
    );

    ApplicationController.addConfiguredNotifications(rlDb, "test", "ben@gmail.com",
        Sets.newHashSet(WorkflowRunnerNotification.DIED_UNCLEAN)
    );

    assertCollectionEquivalent(
        Lists.newArrayList(WorkflowRunnerNotification.DIED_UNCLEAN, WorkflowRunnerNotification.FAILURE),
        getNotifications(WorkflowQueries.getApplicationNotifications(rlDb, test.getId(), "ben@gmail.com"))
    );

    ApplicationController.removeConfiguredNotifications(rlDb, "test", "ben@gmail.com",
        Sets.newHashSet(WorkflowRunnerNotification.DIED_UNCLEAN, WorkflowRunnerNotification.INTERNAL_ERROR)
    );

    assertCollectionEquivalent(
        Lists.newArrayList(WorkflowRunnerNotification.FAILURE),
        getNotifications(WorkflowQueries.getApplicationNotifications(rlDb, test.getId(), "ben@gmail.com")));


  }

  private List<WorkflowRunnerNotification> getNotifications(List<ConfiguredNotification.Attributes> notifs) {
    List<WorkflowRunnerNotification> notifications = Lists.newArrayList();
    for (ConfiguredNotification.Attributes notif : notifs) {
      notifications.add(WorkflowRunnerNotification.findByValue(notif.getWorkflowRunnerNotification()));
    }
    return notifications;
  }
}