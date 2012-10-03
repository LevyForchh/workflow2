package com.rapleaf.cascading_ext.workflow2.action;

import org.apache.hadoop.fs.Path;

import com.rapleaf.cascading_ext.workflow2.Action;
import com.liveramp.cascading_ext.FileSystemHelper;

public class ReleaseLock extends Action {
  private final String pathToLock;
  
  public ReleaseLock(String checkpointToken, String pathToLock) {
    super(checkpointToken);
    this.pathToLock = pathToLock;
  }
  
  @Override
  protected void execute() throws Exception {
    FileSystemHelper.getFS().delete(new Path(pathToLock), true);
  }
}
