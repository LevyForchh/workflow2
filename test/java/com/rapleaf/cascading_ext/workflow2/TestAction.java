package com.rapleaf.cascading_ext.workflow2;

import java.io.IOException;

import com.google.common.collect.Maps;
import org.apache.hadoop.fs.Path;
import org.junit.Test;

import com.rapleaf.cascading_ext.CascadingExtTestCase;
import com.rapleaf.cascading_ext.datastore.BytesDataStore;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class TestAction extends CascadingExtTestCase {
  public class ExampleAction extends Action {
    public ExampleAction() throws IOException {
      super("example");
      creates(new BytesDataStore(getFS(), "example dir 1", getTestRoot(), "/dir1"));
      createsTemporary(new BytesDataStore(getFS(), "example dir 2", getTestRoot(), "/dir2"));
      readsFrom(new BytesDataStore(getFS(), "example dir 3", getTestRoot(), "/dir3"));
    }

    @Override
    protected void execute() throws Exception {
    }
  }

  @Test
  public void testDeletesCreatesAndTemp() throws Exception {
    Path dir1Path = new Path(getTestRoot() + "/dir1");
    getFS().mkdirs(dir1Path);
    Path dir2Path = new Path(getTestRoot() + "/dir2");
    getFS().mkdirs(dir2Path);
    Path dir3Path = new Path(getTestRoot() + "/dir3");
    getFS().mkdirs(dir3Path);

    new ExampleAction().internalExecute(Maps.newHashMap());

    assertFalse("dir2 should be deleted", getFS().exists(dir2Path));
    assertTrue("dir3 should exist", getFS().exists(dir3Path));
  }
}
