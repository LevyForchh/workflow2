package com.rapleaf.cascading_ext.workflow2.action;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.google.common.collect.Lists;
import org.apache.hadoop.io.BytesWritable;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.junit.Test;

import cascading.operation.FunctionCall;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;

import com.rapleaf.cascading_ext.datastore.BucketDataStore;
import com.rapleaf.cascading_ext.map_side_join.Extractor;
import com.rapleaf.cascading_ext.msj_tap.merger.MSJGroup;
import com.rapleaf.cascading_ext.msj_tap.operation.MSJFunction;
import com.rapleaf.cascading_ext.tap.bucket2.PartitionStructure;
import com.rapleaf.cascading_ext.workflow2.WorkflowTestCase;
import com.rapleaf.formats.bucket.Bucket;
import com.rapleaf.formats.test.BucketHelper;
import com.rapleaf.support.SerializationHelper;
import com.rapleaf.support.Strings;
import com.rapleaf.types.new_person_data.StringList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestMSJTapAction extends WorkflowTestCase {

  public static class MockExtractor extends Extractor<Integer> {
    @Override
    public Integer extractKey(byte[] record) {
      return Integer.valueOf(Strings.fromBytes(record).split("@")[0]);
    }

    @Override
    public Extractor<Integer> makeCopy() {
      return new MockExtractor();
    }
  }

  @Test
  public void testMerge() throws Exception {
    Bucket lBucket;
    Bucket rBucket;
    Bucket tBucket;

    String outputDir = getTestRoot() + "/2/output";

    final String bucket1Path = getTestRoot() + "/2/left";
    final String bucket2Path = getTestRoot() + "/2/right";
    final String bucket3Path = getTestRoot() + "/2/third";

    lBucket = Bucket.create(fs, bucket1Path, BytesWritable.class);
    rBucket = Bucket.create(fs, bucket2Path, BytesWritable.class);
    tBucket = Bucket.create(fs, bucket3Path, BytesWritable.class);

    fillWithData(lBucket, "part-1", "1@d", "4@b", "4@c", "7@a", "7@f", "9@a");
    fillWithData(rBucket, "part-1", "0@m", "1@a", "1@c", "7@a", "7@b", "7@c", "9@a", "9@b", "10@x");
    fillWithData(tBucket, "part-1", "3@f", "3@g", "7@z", "11@n");

    execute(new MSJTapAction<>(
            "TestMapSideJoin",
            getTestRoot() + "/tmp",
            new ExtractorsList<Integer>()
                .add(asStore(bucket1Path), new MockExtractor())
                .add(asStore(bucket2Path), new MockExtractor())
                .add(asStore(bucket3Path), new MockExtractor()),
            new MockJoiner(),
            asStore(outputDir),
            PartitionStructure.UNENFORCED
        )
    );

    Bucket oBucket = Bucket.open(fs, outputDir);
    List<byte[]> results = BucketHelper.readBucket(oBucket);

    List<String> expectedResults = new ArrayList<String>();
    expectedResults.add("0@m");
    expectedResults.add("1@a");
    expectedResults.add("1@c");
    expectedResults.add("1@d");
    expectedResults.add("3@f");
    expectedResults.add("3@g");
    expectedResults.add("4@b");
    expectedResults.add("4@c");
    expectedResults.add("7@a");
    expectedResults.add("7@b");
    expectedResults.add("7@c");
    expectedResults.add("7@f");
    expectedResults.add("7@z");
    expectedResults.add("9@a");
    expectedResults.add("9@b");
    expectedResults.add("10@x");
    expectedResults.add("11@n");

    for (int i = 0; i < results.size(); i++) {
      assertEquals(expectedResults.get(i), Strings.fromBytes(results.get(i)));
    }

    assertEquals(BytesWritable.class, oBucket.getRecordClass());

  }

  @Test(expected = RuntimeException.class)
  public void testReverseSorting() throws Exception {
    Bucket lBucket;
    Bucket rBucket;

    String outputDir = getTestRoot() + "/2/output";

    final String bucket1Path = getTestRoot() + "/2/left";
    final String bucket2Path = getTestRoot() + "/2/right";

    lBucket = Bucket.create(fs, bucket1Path, BytesWritable.class);
    rBucket = Bucket.create(fs, bucket2Path, BytesWritable.class);

    fillWithData(lBucket, "part-1", "9@a", "7@f", "7@a", "4@c", "4@b", "1@d");
    fillWithData(rBucket, "part-1", "10@x", "9@b", "9@a", "7@c", "7@b", "7@a", "1@c", "1@a", "0@m");

    execute(new MSJTapAction<>(
        "test-tap",
        getTestRoot() + "/tmp",
        new ExtractorsList<Integer>()
            .add(asStore(bucket1Path), new MockExtractor())
            .add(asStore(bucket2Path), new MockExtractor()),
        new MockJoiner(),
        asStore(outputDir),
        PartitionStructure.UNENFORCED
    ));

    fail();
  }

  public StringList getStringList(String... strings) {
    return new StringList(Lists.newArrayList(strings));
  }


  static class AppenderJoiner extends MSJFunction<String> {

    public AppenderJoiner() {
      super(new Fields("string_list"));
    }

    public static StringList operate(StringList list1, StringList list2) {
      StringList listOut = nonNull(list1, list2);
      listOut.get_strings().set(2, getString(list1) + getString(list2));
      return listOut;
    }

    private static StringList nonNull(StringList list1, StringList list2) {
      return list1 == null ? list2 : list1;
    }

    public static String getString(StringList list1) {
      if (list1 == null) {
        return "null";
      } else {
        return list1.get_strings().get(2);
      }
    }

    @Override
    public void operate(FunctionCall functionCall, MSJGroup<String> group) {

      StringList list1 = group.getFirstRecordOrNull(0, new StringList());
      StringList list2 = group.getFirstRecordOrNull(1, new StringList());

      StringList listOut = operate(list1, list2);

      functionCall.getOutputCollector().add(new Tuple(listOut));
    }
  }

  public static class MockStringExtractor extends Extractor<String> {

    private transient TDeserializer deserializer;

    @Override
    public String extractKey(byte[] record) {
      TDeserializer deSerializer = getDeSerializer();
      StringList list = new StringList();
      try {
        deSerializer.deserialize(list, record);
      } catch (TException e) {
        throw new RuntimeException(e);
      }
      return list.get_strings().get(1);
    }

    @Override
    public Extractor<String> makeCopy() {
      return new MockStringExtractor();
    }

    public TDeserializer getDeSerializer() {
      if (deserializer == null) {
        deserializer = SerializationHelper.getFixedDeserializer();
      }
      return deserializer;
    }
  }

  @Test
  public void testCrazyIndexing() throws Exception {

    BucketDataStore<StringList> bucket1 = bucket("input1",
        getStringList("", "key1", "value1"),
        getStringList("", "key2", "value2"),
        getStringList("", "key3", "value3"),
        getStringList("", "key4", "value4")
    );


    BucketDataStore<StringList> bucket2 = bucket("input2",
        getStringList("", "key1", "value5"),
        getStringList("", "key2", "value6")
    );

    BucketDataStore<StringList> out = emptyBucket("out", StringList.class);

    execute(new MSJTapAction<String>(
            "test-indexing",
            getTestRoot() + "/tmp",
            new ExtractorsList<String>()
                .add(bucket1, new MockStringExtractor())
                .add(bucket2, new MockStringExtractor()),
            new AppenderJoiner(),

            out,
            PartitionStructure.UNENFORCED
        )
    );

    assertTrue(bucketContains(
        out,
        Lists.newArrayList(
            getStringList("", "key1", "value1value5"),
            getStringList("", "key2", "value2value6"),
            getStringList("", "key3", "value3null"),
            getStringList("", "key4", "value4null")
        )
    ));

    BucketDataStore<StringList> out2 = emptyBucket("out2", StringList.class);

    execute(new MSJTapAction<>(
            "test-msj-indexing",
            getTestRoot() + "/tmp",
            new ExtractorsList().add(out, new MockStringExtractor())
                .add(bucket2, new MockStringExtractor()),
            new AppenderJoiner(),
            out2,
            PartitionStructure.UNENFORCED
        )
    );

    assertTrue(bucketContains(
        out2,
        Lists.newArrayList(
            getStringList("", "key1", "value1value5value5"),
            getStringList("", "key2", "value2value6value6"),
            getStringList("", "key3", "value3nullnull"),
            getStringList("", "key4", "value4nullnull")
        )
    ));

  }


  public static class MockJoiner extends MSJFunction<Integer> {

    public MockJoiner() {
      super(new Fields("bytes"));
    }

    @Override
    public void operate(FunctionCall functionCall, MSJGroup<Integer> group) {
      SortedSet<String> values = new TreeSet<String>();
      Set<String> keys = new HashSet<String>();

      for (int i = 0; i < group.getNumIterators(); i++) {
        Iterator<byte[]> iterator = group.getArgumentsIterator(i);

        while (iterator.hasNext()) {
          String[] fields = Strings.fromBytes(iterator.next()).split("@");
          keys.add(fields[0]);
          values.add(fields[1]);
        }
      }

      String key;

      if (keys.size() == 0) {
        if (values.size() != 0) {
          throw new RuntimeException("can only have no keys if there are no values!");
        } else {
          return;
        }
      }

      if (keys.size() == 1) {
        key = keys.iterator().next();
      } else {
        throw new RuntimeException("Can only have one key per group!:" + keys);
      }

      for (String value : values) {
        functionCall.getOutputCollector().add(new Tuple(new BytesWritable(Strings.toBytes(key + "@" + value))));
      }
    }
  }

}