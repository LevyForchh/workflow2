package com.rapleaf.cascading_ext.workflow2.action;

import com.google.common.collect.Sets;
import com.rapleaf.cascading_ext.CascadingExtTestCase;
import com.rapleaf.cascading_ext.datastore.BucketDataStore;
import com.rapleaf.cascading_ext.datastore.internal.DataStoreBuilder;
import com.rapleaf.formats.bucket.Bucket;
import com.rapleaf.formats.stream.RecordOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class TestAddMissingPartitionsToBucket extends CascadingExtTestCase {
  public void testIt() throws Exception {
    BucketDataStore dataStore = new DataStoreBuilder(getTestRoot()).getBytesDataStore("store");
    Bucket bucket = Bucket.open(fs, dataStore.getBucket().getRoot().toString());
    write(bucket, "part-00012_0", "a", "b", "c");
    write(bucket, "part-00061_0", "d", "e", "f");
    new AddMissingPartitionsToBucket("add-missing-partitions", 70, dataStore).execute();
    assertEquals(Sets.<String>newHashSet("a", "b", "c", "d", "e", "f"), readRecords(bucket));
    assertEquals(70, bucket.getStoredFiles().length);
  }

  private Set<String> readRecords(Bucket bucket) {
    Set<String> data = new HashSet<String>();

    for (byte[] record : bucket) {
      data.add(new String(record));
    }

    return data;
  }
  private void write(Bucket bucket, String relPath, String... records) throws IOException {
    RecordOutputStream os = bucket.openWrite(relPath);

    for (String record : records) {
      os.write(record.getBytes());
    }

    os.close();
  }
}
