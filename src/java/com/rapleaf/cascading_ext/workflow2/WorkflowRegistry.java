package com.rapleaf.cascading_ext.workflow2;

import com.liveramp.types.workflow.LiveWorkflowMeta;

public interface WorkflowRegistry {
  public void register(String uuid, LiveWorkflowMeta meta);
  public void deregister();
}
