
/**
 * Autogenerated by Jack
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 */
package com.liveramp.databases.workflow_db.iface;

import com.liveramp.databases.workflow_db.models.ResourceRecord;
import com.liveramp.databases.workflow_db.query.ResourceRecordQueryBuilder;
import com.liveramp.databases.workflow_db.query.ResourceRecordDeleteBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.List;

import com.rapleaf.jack.IModelPersistence;

public interface IResourceRecordPersistence extends IModelPersistence<ResourceRecord> {
  ResourceRecord create(final String name, final int resource_root_id, final String json, final Long created_at, final String class_path) throws IOException;
  ResourceRecord create(final String name, final int resource_root_id, final String json) throws IOException;

  ResourceRecord createDefaultInstance() throws IOException;
  List<ResourceRecord> findByName(String value)  throws IOException;
  List<ResourceRecord> findByResourceRootId(int value)  throws IOException;
  List<ResourceRecord> findByJson(String value)  throws IOException;
  List<ResourceRecord> findByCreatedAt(Long value)  throws IOException;
  List<ResourceRecord> findByClassPath(String value)  throws IOException;

  ResourceRecordQueryBuilder query();

  ResourceRecordDeleteBuilder delete();
}