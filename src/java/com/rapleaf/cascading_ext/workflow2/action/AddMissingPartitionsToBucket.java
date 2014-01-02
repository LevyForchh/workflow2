package com.rapleaf.cascading_ext.workflow2.action;

import com.liveramp.cascading_ext.FileSystemHelper;
import com.rapleaf.cascading_ext.datastore.BucketDataStore;
import com.rapleaf.cascading_ext.datastore.BucketDataStoreImpl;
import com.rapleaf.cascading_ext.workflow2.Action;
import com.rapleaf.formats.bucket.BucketUtil;

public class AddMissingPartitionsToBucket extends Action {
  private final int numPartitions;
  private final BucketDataStore dataStore;
  private final Class recordClass;

  public AddMissingPartitionsToBucket(String checkpointToken, int numPartitions, BucketDataStore dataStore, Class recordClass) {
    super(checkpointToken);
    this.numPartitions = numPartitions;
    this.dataStore = dataStore;
    this.recordClass = recordClass;

    writesTo(dataStore);
  }

  @Override
  protected void execute() throws Exception {
    dataStore.getBucket().markAsMutable();
    BucketUtil.addMissingPartitions(getFS(), dataStore.getPath(), numPartitions, recordClass);
    dataStore.getBucket().markAsImmutable();
  }

  public static void main(String[] args) throws Exception {
    String path = args[0];
    Class clazz = Class.forName(args[1]);
    BucketDataStore dataStore = new BucketDataStoreImpl(FileSystemHelper.getFS(), "bucket", path, "", clazz);
    new AddMissingPartitionsToBucket("checkpoint", 599, dataStore, clazz).execute();
  }
}
