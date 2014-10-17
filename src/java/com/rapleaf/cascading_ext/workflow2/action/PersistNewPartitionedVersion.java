package com.rapleaf.cascading_ext.workflow2.action;

import java.io.IOException;

import com.google.common.collect.ListMultimap;
import org.apache.thrift.TBase;

import com.rapleaf.cascading_ext.datastore.PartitionedDataStore;
import com.rapleaf.cascading_ext.workflow2.Action;


public class PersistNewPartitionedVersion<T extends TBase> extends Action {

  private final PartitionedDataStore destinationStore;
  private final PartitionedDataStore sourceStore;
  private final ListMultimap<Long, Integer> persistedVersions;

  public PersistNewPartitionedVersion(String checkpointToken,
                                      PartitionedDataStore<? extends T> sourceStore,
                                      PartitionedDataStore<? extends T> destinationStore,
                                      ListMultimap<Long, Integer> persistedVersions) throws IOException {
    super(checkpointToken);

    this.destinationStore = destinationStore;
    this.sourceStore = sourceStore;
    this.persistedVersions = persistedVersions;

    readsFrom(sourceStore);
    writesTo(destinationStore);
  }

  @Override
  protected void execute() throws Exception {
    destinationStore.persistFrom(sourceStore, persistedVersions);
  }
}
