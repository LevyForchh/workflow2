package com.liveramp.workflow_state;

public enum ProcessStatus {
  ALIVE,
  TIMED_OUT,
  STOPPED;

  public static ProcessStatus findByValue(int val){
    return values()[val];
  }

}
