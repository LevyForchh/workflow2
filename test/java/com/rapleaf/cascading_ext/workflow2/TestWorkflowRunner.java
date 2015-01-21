package com.rapleaf.cascading_ext.workflow2;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import junit.framework.Assert;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Test;

import cascading.tap.Tap;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;

import com.rapleaf.cascading_ext.CascadingExtTestCase;
import com.rapleaf.cascading_ext.HRap;
import com.rapleaf.cascading_ext.datastore.DataStore;
import com.rapleaf.cascading_ext.datastore.TupleDataStore;
import com.rapleaf.cascading_ext.datastore.TupleDataStoreImpl;
import com.rapleaf.cascading_ext.workflow2.action.NoOpAction;
import com.rapleaf.cascading_ext.workflow2.context.HdfsContextStorage;
import com.rapleaf.cascading_ext.workflow2.options.TestWorkflowOptions;
import com.rapleaf.cascading_ext.workflow2.state.HdfsCheckpointPersistence;
import com.rapleaf.cascading_ext.workflow2.state.StepStatus;
import com.rapleaf.cascading_ext.workflow2.state.WorkflowStatePersistence;
import com.rapleaf.formats.test.TupleDataStoreHelper;
import com.rapleaf.support.event_timer.TimedEvent;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;

public class TestWorkflowRunner extends CascadingExtTestCase {

  public static class FailingAction extends Action {
    public FailingAction(String checkpointToken) {
      super(checkpointToken);
    }

    @Override
    public void execute() {
      throw new RuntimeException("failed on purpose");
    }
  }

  public static class DelayedFailingAction extends Action {
    private final Semaphore semaphore;

    public DelayedFailingAction(String checkpointToken, Semaphore sem) {
      super(checkpointToken);
      this.semaphore = sem;
    }

    @Override
    public void execute() throws InterruptedException {
      semaphore.acquire();
      throw new RuntimeException("failed on purpose");
    }
  }

  public static class FlipAction extends Action {

    private final AtomicBoolean val;

    public FlipAction(String checkpointToken, AtomicBoolean val) {
      super(checkpointToken);
      this.val = val;
    }

    @Override
    public void execute() {
      val.set(true);
    }
  }

  public static class IncrementAction extends Action {
    public IncrementAction(String checkpointToken) {
      super(checkpointToken);
    }

    public static int counter = 0;

    @Override
    public void execute() {
      counter++;
    }
  }

  public static class IncrementAction2 extends Action {

    private final AtomicInteger counter;

    public IncrementAction2(String checkpointToken, AtomicInteger counter) {
      super(checkpointToken);
      this.counter = counter;
    }

    @Override
    public void execute() {
      counter.incrementAndGet();
    }

  }


  public static class LockedAction extends Action {

    private final Semaphore semaphore;

    public LockedAction(String checkpointToken, Semaphore semaphore) {
      super(checkpointToken);
      this.semaphore = semaphore;
    }

    @Override
    protected void execute() throws Exception {
      semaphore.acquire();
    }
  }

  public static class UnlockWaitAction extends Action {

    private final Semaphore toUnlock;
    private final Semaphore toAwait;


    public UnlockWaitAction(String checkpointToken, Semaphore toUnlock,
                            Semaphore toAwait) {
      super(checkpointToken);
      this.toUnlock = toUnlock;
      this.toAwait = toAwait;
    }

    @Override
    protected void execute() throws Exception {
      toUnlock.release();
      toAwait.acquire();
    }
  }


  private final String checkpointDir = getTestRoot() + "/checkpoints";

  @Before
  public void prepare() throws Exception {
    IncrementAction.counter = 0;
  }

  @Test
  public void testSimple() throws Exception {
    Step first = new Step(new IncrementAction("first"));
    Step second = new Step(new IncrementAction("second"), first);

    executeWorkflow(second);

    assertEquals(2, IncrementAction.counter);
  }

  @Test
  public void testWritesCheckpoints() throws Exception {
    Step first = new Step(new IncrementAction("first"));
    Step second = new Step(new FailingAction("second"), first);

    try {
      executeWorkflow(second);
      fail("should have failed!");
    } catch (Exception e) {
      // expected
    }

    assertEquals(1, IncrementAction.counter);
    assertTrue(getFS().exists(new Path(checkpointDir + "/first")));
  }

  @Test
  public void testResume() throws Exception {
    Step first = new Step(new IncrementAction("first"));
    Step second = new Step(new IncrementAction("second"), first);

    getFS().createNewFile(new Path(checkpointDir + "/first"));

    new WorkflowRunner("test", checkpointDir,
        new TestWorkflowOptions(),
        second).run();

    assertEquals(1, IncrementAction.counter);
  }

  @Test
  public void testFullRestart1() throws IOException {

    //  test a full restart if interrupted by a failure

    WorkflowStatePersistence peristence = new HdfsCheckpointPersistence(getTestRoot() + "/checkpoints");

    AtomicInteger int1 = new AtomicInteger(0);
    AtomicInteger int2 = new AtomicInteger(0);

    Step one = new Step(new IncrementAction2("one", int1));
    Step two = new Step(new FailingAction("two"), one);
    Step three = new Step(new IncrementAction2("three", int2), two);

    WorkflowRunner run = new WorkflowRunner("Test Workflow", peristence, new TestWorkflowOptions(), Sets.newHashSet(three));

    try {
      run.run();
      fail();
    } catch (Exception e) {
      //  no-op
    }

    assertEquals(1, int1.intValue());
    assertEquals(0, int2.intValue());

    peristence = new HdfsCheckpointPersistence(getTestRoot() + "/checkpoints");

    one = new Step(new IncrementAction2("one", int1));
    two = new Step(new NoOpAction("two"), one);
    three = new Step(new IncrementAction2("three", int2), two);

    run = new WorkflowRunner("Test Workflow", peristence, new TestWorkflowOptions(), Sets.newHashSet(three));
    run.run();

    assertEquals(1, int1.intValue());
    assertEquals(1, int2.intValue());

  }

  @Test
  public void testLoneMultiStepAction() throws Exception {
    // lone multi
    Step s = new Step(new MultiStepAction("lone", Arrays.asList(new Step(
        new IncrementAction("blah")))));

    executeWorkflow(s);

    assertEquals(1, IncrementAction.counter);
  }

  @Test
  public void testMultiInTheMiddle() throws Exception {
    Step s = new Step(new IncrementAction("first"));
    s = new Step(new MultiStepAction("lone", Arrays.asList(new Step(new IncrementAction("blah")))),
        s);
    s = new Step(new IncrementAction("last"), s);

    executeWorkflow(s);

    assertEquals(3, IncrementAction.counter);
  }

  @Test
  public void testMultiAtTheEnd() throws Exception {
    Step s = new Step(new IncrementAction("first"));
    s = new Step(new MultiStepAction("lone", Arrays.asList(new Step(new IncrementAction("blah")))),
        s);

    executeWorkflow(s);

    assertEquals(2, IncrementAction.counter);
  }

  private static Thread run(final WorkflowRunner runner, final Wrapper<Exception> exception) {
    return new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          runner.run();
        } catch (Exception e) {
          exception.setVal(e);
        }
      }
    });
  }

  @Test
  public void testFailThenShutdown() throws InterruptedException {

    WorkflowStatePersistence peristence = new HdfsCheckpointPersistence(getTestRoot() + "/checkpoints");

    Semaphore semaphore = new Semaphore(0);
    Semaphore semaphore2 = new Semaphore(0);
    AtomicBoolean didExecute = new AtomicBoolean();

    Step fail = new Step(new DelayedFailingAction("fail", semaphore));
    Step unlockFail = new Step(new UnlockWaitAction("unlock", semaphore, semaphore2));
    Step last = new Step(new FlipAction("after", didExecute), unlockFail);

    Wrapper<Exception> exception = new Wrapper<Exception>();
    WorkflowRunner run = new WorkflowRunner("Test Workflow", peristence, new TestWorkflowOptions().setMaxConcurrentSteps(2),
        Sets.newHashSet(fail, last));

    Thread t = run(run, exception);
    t.start();

    Thread.sleep(500);
    peristence.markShutdownRequested("Shutdown Requested");
    semaphore2.release();

    t.join();

    Exception failure = exception.getVal();
    assertTrue(failure.getMessage().contains("(1/1) Step fail failed with exception: failed on purpose"));

    assertEquals("Shutdown Requested", peristence.getShutdownRequest());
    assertFalse(didExecute.get());
    assertTrue(peristence.getStepStatuses().get("fail").getStatus() == StepStatus.FAILED);
    assertTrue(peristence.getStepStatuses().get("unlock").getStatus() == StepStatus.COMPLETED);
    assertTrue(peristence.getStepStatuses().get("after").getStatus() == StepStatus.WAITING);

  }

  @Test
  public void testShutdownThenFail() throws InterruptedException {

    Semaphore semaphore = new Semaphore(0);
    AtomicBoolean didExecute = new AtomicBoolean(false);

    WorkflowStatePersistence peristence = new HdfsCheckpointPersistence(getTestRoot() + "/checkpoints");

    Step fail = new Step(new DelayedFailingAction("fail", semaphore));
    Step after = new Step(new FlipAction("after", didExecute), fail);

    Wrapper<Exception> exception = new Wrapper<Exception>();
    WorkflowRunner run = new WorkflowRunner("Test Workflow", peristence, new TestWorkflowOptions(), Sets.newHashSet(after));

    Thread t = run(run, exception);
    t.start();

    Thread.sleep(500);
    peristence.markShutdownRequested("Shutdown Requested");

    semaphore.release();

    t.join();

    Exception failure = exception.getVal();
    assertTrue(failure.getMessage().contains("(1/1) Step fail failed with exception: failed on purpose"));

    assertEquals("Shutdown Requested", peristence.getShutdownRequest());
    assertFalse(didExecute.get());
    assertTrue(peristence.getStepStatuses().get("fail").getStatus() == StepStatus.FAILED);
    assertTrue(peristence.getStepStatuses().get("after").getStatus() == StepStatus.WAITING);
  }

  @Test
  public void testShutdown() throws InterruptedException {

    WorkflowStatePersistence peristence = new HdfsCheckpointPersistence(getTestRoot() + "/checkpoints");

    Semaphore semaphore = new Semaphore(0);
    AtomicInteger preCounter = new AtomicInteger(0);
    AtomicInteger postConter = new AtomicInteger(0);

    Step pre = new Step(new IncrementAction2("pre", preCounter));
    Step step = new Step(new LockedAction("wait", semaphore), pre);
    Step after = new Step(new IncrementAction2("after", postConter), step);

    Wrapper<Exception> exception = new Wrapper<Exception>();
    WorkflowRunner run = new WorkflowRunner("Test Workflow", peristence, new TestWorkflowOptions(), Sets.newHashSet(after));

    Thread t = run(run, exception);
    t.start();

    Thread.sleep(500);
    peristence.markShutdownRequested("Shutdown Requested");

    semaphore.release();

    t.join();

    Exception failure = exception.getVal();

    assertEquals("Shutdown requested: Test Workflow. Reason: Shutdown Requested", failure.getMessage());

    assertEquals("Shutdown Requested", peristence.getShutdownRequest());
    assertEquals(1, preCounter.get());
    assertEquals(0, postConter.get());
    assertTrue(peristence.getStepStatuses().get("pre").getStatus() == StepStatus.COMPLETED);
    assertTrue(peristence.getStepStatuses().get("wait").getStatus() == StepStatus.COMPLETED);
    assertTrue(peristence.getStepStatuses().get("after").getStatus() == StepStatus.WAITING);

    //  restart

    peristence = new HdfsCheckpointPersistence(getTestRoot() + "/checkpoints");
    run = new WorkflowRunner("Test Workflow", peristence, new TestWorkflowOptions(), Sets.newHashSet(after));

    t = run(run, exception);

    t.start();
    semaphore.release();
    t.join();

    assertEquals(null, peristence.getShutdownRequest());
    assertEquals(1, preCounter.get());
    assertEquals(1, postConter.get());
    assertTrue(peristence.getStepStatuses().get("pre").getStatus() == StepStatus.SKIPPED);
    assertTrue(peristence.getStepStatuses().get("wait").getStatus() == StepStatus.SKIPPED);
    assertTrue(peristence.getStepStatuses().get("after").getStatus() == StepStatus.COMPLETED);

  }

  private static class Wrapper<T> {
    private T val;

    public T getVal() {

      if (val == null) {
        org.junit.Assert.fail("Expected value to be set!");
      }

      return val;
    }

    public void setVal(T val) {
      this.val = val;
    }
  }

  @Test
  public void testMultiInMultiEnd() throws Exception {
    Step s = new Step(new IncrementAction("first"));
    // please, never do this in real code
    s = new Step(new MultiStepAction("depth 1", Arrays.asList(new Step(new MultiStepAction(
        "depth 2", Arrays.asList(new Step(new IncrementAction("blah"))))))), s);
    s = new Step(new IncrementAction("last"), s);

    executeWorkflow(s);

    assertEquals(3, IncrementAction.counter);
  }

  @Test
  public void testMulitInMultiMiddle() throws Exception {
    Step b = new Step(new IncrementAction("b"));
    Step innermost = new Step(new MultiStepAction("innermost", Arrays.asList(new Step(
        new IncrementAction("c")))), b);
    Step d = new Step(new IncrementAction("d"), b);

    Step a = new Step(new IncrementAction("a"));

    Step outer = new Step(new MultiStepAction("outer", Arrays.asList(b, innermost, d)), a);

    executeWorkflow(outer);

    assertEquals(4, IncrementAction.counter);
  }

  @Test
  public void testDuplicateCheckpoints() throws Exception {
    try {
      executeWorkflow(Sets.<Step>newHashSet(
              new Step(new IncrementAction("a")),
              new Step(new IncrementAction("a"))),
          checkpointDir);

      fail("should have thrown an exception");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void testTimingMultiStep() throws Exception {

    Step bottom1 = new Step(new IncrementAction("bottom1"));
    Step bottom2 = new Step(new IncrementAction("bottom2"));

    Step multiMiddle = new Step(new MultiStepAction("middle", Arrays.asList(bottom1, bottom2)));
    Step flatMiddle = new Step(new IncrementAction("flatMiddle"));

    Step top = new Step(new MultiStepAction("Tom's first test dude", Arrays.asList(multiMiddle, flatMiddle)));

    WorkflowRunner testWorkflow = new WorkflowRunner("", checkpointDir,
        new TestWorkflowOptions(),
        top);
    testWorkflow.run();

    assertTrue(testWorkflow.getTimer() != null);

    // Goal here is to detect whether nested MultiTimedEvents ever have "-1"s in their timing and to FAIL if this occurs.

    // Assert that none of the timer.EventStartTime values are -1
    assertTrue(testWorkflow.getTimer().getEventStartTime() != -1);

    TimedEvent middleTimer = multiMiddle.getTimer();
    TimedEvent flatMiddleTimer = flatMiddle.getTimer();

    //    System.out.println("CHILDREN:");
    assertTrue(middleTimer.getEventStartTime() != -1);
    assertTrue(flatMiddleTimer.getEventStartTime() != -1);

    TimedEvent bottom1Timer = bottom1.getTimer();
    TimedEvent bottom2Timer = bottom1.getTimer();

    //    System.out.println("SUBCHILDREN:");
    assertTrue(bottom1Timer.getEventStartTime() != -1);
    assertTrue(bottom2Timer.getEventStartTime() != -1);
  }

  @Test
  public void testSandboxDir() throws Exception {
    try {
      WorkflowRunner wfr = new WorkflowRunner("", checkpointDir,
          new TestWorkflowOptions(),
          fakeStep("a", "/fake/EVIL/../path"),
          fakeStep("b", "/path/of/fakeness"));
      wfr.setSandboxDir("//fake/path");
      wfr.run();
      fail("There was an invalid path!");
    } catch (IOException e) {
      // expected
    }


    try {
      WorkflowRunner wfr = new WorkflowRunner("", checkpointDir,
          new TestWorkflowOptions(),
          fakeStep("a", "/fake/EVIL/../path"),
          fakeStep("b", "/fake/./path"));
      wfr.setSandboxDir("//fake/path");
      wfr.run();
      // expected
    } catch (IOException e) {
      fail(e.getMessage());
    }
  }

  public Step fakeStep(String checkpointToken, final String fakePath) {
    DataStore dataStore = new DataStore() {
      private String path = fakePath;

      @Override
      public String getName() {
        return "fakeDataStore";
      }

      @Override
      public Tap getTap() {
        return null;
      }

      @Override
      public String getPath() {
        return path;
      }

      @Override
      public String getRelPath() {
        return "." + path;
      }
    };
    Action action = new IncrementAction(checkpointToken);
    action.creates(dataStore);
    action.createsTemporary(dataStore);
    return new Step(action);
  }

  @Test
  public void testPathNesting() throws IOException, ClassNotFoundException {

    String tmpRoot = getTestRoot() + "/tmp-dir";

    Step step = new Step(new ParentResource("parent-step", tmpRoot));

    HdfsContextStorage storage = new HdfsContextStorage(getTestRoot() + "/context");

    TestWorkflowOptions options = new TestWorkflowOptions()
        .setStorage(storage);

    WorkflowStatePersistence persistence = new HdfsCheckpointPersistence(getTestRoot() + "/checkpoints");

    new WorkflowRunner(
        "test workflow",
        persistence,
        options,
        Sets.newHashSet(step)
    ).run();

    Resource<Integer> resMock1 = new Resource<Integer>("resource", new ActionId("parent-step")
        .setParentPrefix(""));
    Resource<Integer> resMock2 = new Resource<Integer>("output", new ActionId("consume-resource")
        .setParentPrefix("parent-step__"));

    assertEquals(1, storage.get(resMock1).intValue());
    assertEquals(1, storage.get(resMock2).intValue());

    Assert.assertEquals(StepStatus.COMPLETED, persistence.getState("parent-step__set-resource").getStatus());
    Assert.assertEquals(StepStatus.COMPLETED, persistence.getState("parent-step__consume-resource").getStatus());

    TupleDataStore store = new TupleDataStoreImpl("store", tmpRoot+"/parent-step-tmp-stores/consume-resource-tmp-stores/", "tup_out", new Fields("string"));
    List<Tuple> tups = HRap.getAllTuples(store.getTap());

    assertCollectionEquivalent(Sets.newHashSet(tups), Lists.<Tuple>newArrayList(new Tuple(1)));

  }

  public static class ParentResource extends MultiStepAction {

    public ParentResource(String checkpointToken, String tmpRoot) throws IOException {
      super(checkpointToken, tmpRoot);

      Resource<Integer> res = resource("resource");

      Step set = new Step(new SetResource(
          "set-resource",
          res
      ));

      Step get = new Step(new ConsumeResource(
          "consume-resource",
          getTmpRoot(),
          res),
          set
      );

      setSubStepsFromTail(get);

    }

  }

  public static class SetResource extends Action {

    private final Resource<Integer> res;

    public SetResource(String checkpointToken,
                       Resource<Integer> res1) {
      super(checkpointToken);
      this.res = res1;
      creates(res1);
    }

    @Override
    protected void execute() throws Exception {
      set(res, 1);
    }
  }

  public static class ConsumeResource extends Action {

    private final Resource<Integer> res;

    private final Resource<Integer> resOut;
    private final TupleDataStore tupOut;

    public ConsumeResource(String checkpointToken,
                           String tmpRoot,
                           Resource<Integer> res1) throws IOException {
      super(checkpointToken, tmpRoot);
      this.res = res1;
      uses(res);

      this.tupOut = builder().getTupleDataStore("tup_out", new Fields("string"));
      this.resOut = resource("output");
      creates(resOut);
    }

    @Override
    protected void execute() throws Exception {
      Integer val = get(res);
      set(resOut, val);

      TupleDataStoreHelper.writeToStore(tupOut,
          new Tuple(val)
      );
    }
  }

}
