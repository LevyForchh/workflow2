package com.rapleaf.cascading_ext.workflow2;

import java.util.*;

public class WorkflowRunnerOptions {

  private int maxConcurrentSteps;
  private Integer webUiPort;
  private List<String> notificationEmails;
  private Set<WorkflowRunner.NotificationType> enabledNotifications;

  public WorkflowRunnerOptions() {
    maxConcurrentSteps = Integer.MAX_VALUE;
    webUiPort = null;
    notificationEmails = null;
    enabledNotifications = EnumSet.allOf(WorkflowRunner.NotificationType.class);
  }

  public int getMaxConcurrentSteps() {
    return maxConcurrentSteps;
  }

  public WorkflowRunnerOptions setMaxConcurrentSteps(int maxConcurrentSteps) {
    this.maxConcurrentSteps = maxConcurrentSteps;
    return this;
  }

  public Integer getWebUiPort() {
    return webUiPort;
  }

  public WorkflowRunnerOptions setWebUiPort(Integer webUiPort) {
    this.webUiPort = webUiPort;
    return this;
  }

  public List<String> getNotificationEmails() {
    return notificationEmails;
  }

  public WorkflowRunnerOptions setNotificationEmails(String... notificationEmails) {
    this.notificationEmails = Arrays.asList(notificationEmails);
    return this;
  }

  public WorkflowRunnerOptions setNotificationEmails(List<String> notificationEmails) {
    this.notificationEmails = notificationEmails;
    return this;
  }

  public void setEnabledNotifications(WorkflowRunner.NotificationType... enabledNotifications) {
    this.enabledNotifications = new HashSet<WorkflowRunner.NotificationType>(Arrays.asList(enabledNotifications));
  }

  public void setEnabledNotifications(Set<WorkflowRunner.NotificationType> enabledNotifications) {
    this.enabledNotifications = enabledNotifications;
  }

  public Set<WorkflowRunner.NotificationType> getEnabledNotifications() {
    return enabledNotifications;
  }
}
