package com.rapleaf.cascading_ext.workflow2.action;

import java.io.IOException;

import org.apache.thrift.TBase;

import com.rapleaf.cascading_ext.datastore.PartitionedDataStore;
import com.rapleaf.cascading_ext.workflow2.Action;


public class PersistNewPartitionedVersion<T extends TBase> extends Action {

  private final PartitionedDataStore destinationStore;
  private final PartitionedDataStore sourceStore;
  private final Class klass;

  public PersistNewPartitionedVersion(String checkpointToken, String tmpRoot, Class klass,
                                      PartitionedDataStore<? extends T> sourceStore,
                                      PartitionedDataStore<? extends T> destinationStore) throws IOException {
    super(checkpointToken, tmpRoot);

    this.destinationStore = destinationStore;
    this.sourceStore = sourceStore;
    this.klass = klass;
  }

  @Override
  protected void execute() throws Exception {
    destinationStore.persistFrom(sourceStore);
  }
}
