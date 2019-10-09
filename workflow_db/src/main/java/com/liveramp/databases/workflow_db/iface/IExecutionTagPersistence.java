
/**
 * Autogenerated by Jack
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 */
package com.liveramp.databases.workflow_db.iface;

import com.liveramp.databases.workflow_db.models.ExecutionTag;
import com.liveramp.databases.workflow_db.query.ExecutionTagQueryBuilder;
import com.liveramp.databases.workflow_db.query.ExecutionTagDeleteBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.List;

import com.rapleaf.jack.IModelPersistence;

public interface IExecutionTagPersistence extends IModelPersistence<ExecutionTag> {
  ExecutionTag create(final int workflow_execution_id, final String tag, final String value) throws IOException;

  ExecutionTag createDefaultInstance() throws IOException;
  List<ExecutionTag> findByWorkflowExecutionId(int value)  throws IOException;
  List<ExecutionTag> findByTag(String value)  throws IOException;
  List<ExecutionTag> findByValue(String value)  throws IOException;

  ExecutionTagQueryBuilder query();

  ExecutionTagDeleteBuilder delete();
}
