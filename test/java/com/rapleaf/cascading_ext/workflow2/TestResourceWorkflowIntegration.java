package com.rapleaf.cascading_ext.workflow2;

import java.io.IOException;
import java.util.Set;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import com.liveramp.cascading_ext.resource.ReadResource;
import com.liveramp.cascading_ext.resource.Resource;
import com.liveramp.cascading_ext.resource.ResourceManager;
import com.liveramp.cascading_ext.resource.ResourceManagers;
import com.liveramp.cascading_ext.resource.WriteResource;
import com.rapleaf.cascading_ext.workflow2.options.TestWorkflowOptions;
import com.rapleaf.cascading_ext.workflow2.state.DbPersistenceFactory;
import com.rapleaf.db_schemas.DatabasesImpl;
import com.rapleaf.db_schemas.rldb.IRlDb;

public class TestResourceWorkflowIntegration extends WorkflowTestCase {

  private static final String name = ResourceTest.class.getName();
  private static final Set<Long> contextNumbers = Sets.newHashSet(2L, 4L, 8L, 16L);
  private static final Set<Long> previousNumbers = Sets.newHashSet(0L, 1L);
  private static final Set<Long> expectedNumbers = Sets.newHashSet(1L, 2L, 3L, 4L, 5L);
  private static boolean shouldFail;
  private IRlDb rlDb;

  @Before
  public void before() throws IOException {
    shouldFail = false;
    rlDb = new DatabasesImpl().getRlDb();
    rlDb.deleteAll();
  }

  @Test
  public void testHdfsStorage() throws IOException {
    testStorage(new RMFactory() {
      @Override
      public ResourceManager make() throws IOException {
        return ResourceManagers.hdfsResourceManager(getTestRoot() + "/" + name, name, null, rlDb);
      }
    });
  }

  @Test
  public void testDbStorage() throws IOException {
    testStorage(new RMFactory() {
      @Override
      public ResourceManager make() throws IOException {
        return ResourceManagers.dbResourceManager(name, null, rlDb);
      }
    });
  }

  private <T> void testStorage(RMFactory manager) throws IOException {
    WorkflowRunner runner = getRunner(manager.make(), previousNumbers);
    runner.run();

    // should fail the first time
    shouldFail = true;
    runner = getRunner(manager.make(), expectedNumbers);
    try {
      runner.run();
    } catch (RuntimeException e) {
    }

    shouldFail = false;
    runner = getRunner(manager.make(), expectedNumbers);
    runner.run();
  }

  private <T> WorkflowRunner getRunner(ResourceManager manager, Set<Long> numbersToWrite) throws IOException {
    Step resourceTest = new Step(new ResourceTest(manager, getTestRoot(), numbersToWrite));
    return new WorkflowRunner(
        ResourceTest.class,
        new DbPersistenceFactory(),
        new TestWorkflowOptions().setResourceManager(manager),
        resourceTest
    );
  }

  private static class ResourceTest extends MultiStepAction {
    private MyContext context;

    public ResourceTest(ResourceManager<String, ?> manager, String tmpRoot, Set<Long> numbersToWrite) {
      super("checkpoints", tmpRoot);
      context = manager.manage(new MyContext());

      Resource<Set<Long>> resource = manager.resource(context.numbers());

      Step step1 = new Step(new CreatesAction("create_step", resource, numbersToWrite));
      Step step2 = new Step(new ThrowsAction("throw_step"), step1);
      Step step3 = new Step(new ReadsAction("read_step", resource, numbersToWrite), step2);

      setSubStepsFromTail(step3);
    }
  }

  private static class MyContext {
    public MyContext() {
    }

    ;

    public Set<Long> numbers() {
      return contextNumbers;
    }
  }

  private static class CreatesAction extends Action {
    private final Set<Long> numbersToWrite;
    private WriteResource<Set<Long>> numbers;

    public CreatesAction(String checkpointToken, Resource<Set<Long>> numbers, Set<Long> numbersToWrite) {
      super(checkpointToken);
      this.numbersToWrite = numbersToWrite;
      this.numbers = creates(numbers);
    }

    @Override
    protected void execute() throws Exception {
      set(numbers, numbersToWrite);
    }
  }

  private static class ThrowsAction extends Action {

    public ThrowsAction(String checkpointToken) {
      super(checkpointToken);
    }

    @Override
    protected void execute() throws Exception {
      if (shouldFail) {
        throw new RuntimeException("Failing intentionally");
      }
    }
  }

  private static class ReadsAction extends Action {
    private ReadResource<Set<Long>> numbers;
    private Set<Long> expectedNumbers;

    public ReadsAction(String checkpointToken, Resource<Set<Long>> numbers, Set<Long> expectedNumbers) {
      super(checkpointToken);
      this.numbers = readsFrom(numbers);
      this.expectedNumbers = expectedNumbers;
    }

    @Override
    protected void execute() throws Exception {
      assertCollectionEquivalent(expectedNumbers, get(numbers));
    }
  }

  private interface RMFactory {

    ResourceManager make() throws IOException;

  }
}
