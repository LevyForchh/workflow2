package com.liveramp.workflow.serialization;

import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.mapred.JobConf;
import org.junit.Before;
import org.junit.Test;

import cascading.flow.FlowProcess;
import cascading.operation.Identity;
import cascading.pipe.Each;
import cascading.pipe.GroupBy;
import cascading.pipe.Pipe;
import cascading.scheme.hadoop.SequenceFile;
import cascading.tap.hadoop.Hfs;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;
import cascading.tuple.TupleEntryCollector;
import cascading.tuple.TupleEntryIterator;

import com.rapleaf.cascading_ext.CascadingHelper;
import com.rapleaf.cascading_ext.test.HadoopCommonJunit4TestCase;
import com.rapleaf.cascading_ext.workflow2.WorkflowTestCase;
import com.rapleaf.db_schemas.rldb.models.CookieMonsterReaderDaysum;
import com.rapleaf.db_schemas.rldb.models.DataPlanFieldUsage;
import com.rapleaf.db_schemas.rldb.models.ElmoDaysum;
import com.rapleaf.db_schemas.rldb.models.ElmoPublisherVolume;
import com.rapleaf.db_schemas.rldb.models.PersonalizationApiRequestDaysum;
import com.rapleaf.db_schemas.rldb.models.SpruceQaDaysum;

import static org.junit.Assert.assertEquals;

public class TestJackModelSerialization extends WorkflowTestCase {

  private final String input = getTestRoot() + "/input";

  @Before
  public void setUp() throws Exception {
    CascadingHelper.get().addSerializationToken(204, SpruceQaDaysum.class);
    CascadingHelper.get().addSerializationToken(205, ElmoPublisherVolume.class);
    CascadingHelper.get().addSerializationToken(206, DataPlanFieldUsage.class);
    CascadingHelper.get().addSerializationToken(207, ElmoDaysum.class);
    CascadingHelper.get().addSerializationToken(214, CookieMonsterReaderDaysum.class);
  }

  @Test
  public void testSerialization() throws Exception {
    PersonalizationApiRequestDaysum obj1 = new PersonalizationApiRequestDaysum(1, 1, 1, "", 1, 1,
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
    PersonalizationApiRequestDaysum obj2 = new PersonalizationApiRequestDaysum(2, 2, 2, "", 2, 2,
      2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2);
    PersonalizationApiRequestDaysum obj3 = new PersonalizationApiRequestDaysum(3, 3, 3, "", 3, 3,
      3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3);

    List<PersonalizationApiRequestDaysum> daysums1 = runFlowAndGetOutput(getTestRoot()+"/output1", obj1, obj2, obj1, obj3);
    List<PersonalizationApiRequestDaysum> daysums2 = runFlowAndGetOutput(getTestRoot()+"/output2", obj1, obj3, obj2, obj1);

    // Check that the output is sorted consistently
    assertEquals(daysums1, daysums2);
  }

  private List<PersonalizationApiRequestDaysum> runFlowAndGetOutput(String output, PersonalizationApiRequestDaysum... objs) throws Exception {
    FlowProcess<JobConf> fp = CascadingHelper.get().getFlowProcess();

    Hfs source = new Hfs(new SequenceFile(new Fields("jack_obj")), input);
    TupleEntryCollector tec = source.openForWrite(fp);

    for (PersonalizationApiRequestDaysum obj : objs) {
      tec.add(new Tuple(obj));
    }
    tec.close();

    Hfs sink = new Hfs(new SequenceFile(new Fields("jack_obj")), output);

    Pipe pipe = new Pipe("pipe");
    pipe = new Each(pipe, new Fields("jack_obj"), new Identity());
    pipe = new GroupBy(pipe, new Fields("jack_obj"));

    HadoopCommonJunit4TestCase.flowConnector().connect(source, sink, pipe).complete();

    TupleEntryIterator it = sink.openForRead(fp);

    List<PersonalizationApiRequestDaysum> daysums = new ArrayList<PersonalizationApiRequestDaysum>();
    while (it.hasNext()) {
      TupleEntry te = it.next();
      PersonalizationApiRequestDaysum desObj = (PersonalizationApiRequestDaysum) te.getObject(0);
      daysums.add(desObj);
    }
    return daysums;
  }
}
