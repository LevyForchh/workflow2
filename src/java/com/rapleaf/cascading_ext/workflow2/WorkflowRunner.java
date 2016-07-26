package com.rapleaf.cascading_ext.workflow2;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.liveramp.cascading_ext.megadesk.StoreReaderLocker;
import com.liveramp.cascading_ext.resource.ResourceManager;
import com.liveramp.commons.collections.properties.NestedProperties;
import com.liveramp.commons.collections.properties.OverridableProperties;
import com.liveramp.java_support.alerts_handler.AlertMessages;
import com.liveramp.java_support.alerts_handler.AlertsHandler;
import com.liveramp.java_support.alerts_handler.recipients.AlertRecipients;
import com.liveramp.java_support.alerts_handler.recipients.AlertSeverity;
import com.liveramp.workflow.types.StepStatus;
import com.liveramp.workflow_state.DSAction;
import com.liveramp.workflow_state.StepState;
import com.liveramp.workflow_state.WorkflowConstants;
import com.liveramp.workflow_state.WorkflowEnums;
import com.liveramp.workflow_state.WorkflowRunnerNotification;
import com.liveramp.workflow_state.WorkflowStatePersistence;
import com.rapleaf.cascading_ext.CascadingHelper;
import com.rapleaf.cascading_ext.datastore.DataStore;
import com.rapleaf.cascading_ext.workflow2.counter.CounterFilter;
import com.rapleaf.cascading_ext.workflow2.options.WorkflowOptions;
import com.rapleaf.cascading_ext.workflow2.state.DbPersistenceFactory;
import com.rapleaf.cascading_ext.workflow2.state.InitializedWorkflow;
import com.rapleaf.cascading_ext.workflow2.state.MultiShutdownHook;
import com.rapleaf.cascading_ext.workflow2.state.WorkflowPersistenceFactory;
import com.rapleaf.cascading_ext.workflow2.util.TimeFormatting;

public final class WorkflowRunner {
  private static final Logger LOG = LoggerFactory.getLogger(WorkflowRunner.class);

  public static final String JOB_PRIORITY_PARAM = "mapred.job.priority";
  public static final String JOB_POOL_PARAM = "mapreduce.job.queuename";

  private final WorkflowStatePersistence persistence;
  private final StoreReaderLocker lockProvider;
  private final ContextStorage storage;
  private final int stepPollInterval;

  //  set this if something fails in a step (outside user-code) so we don't keep trying to start steps
  private List<Exception> internalErrors = new CopyOnWriteArrayList<Exception>();

  private OverridableProperties workflowJobProperties;

  /**
   * how many components will we allow to execute simultaneously?
   */
  private final int maxConcurrentSteps;

  private final DirectedGraph<Step, DefaultEdge> dependencyGraph;

  /**
   * semaphore used to control the max number of running components
   */
  private final Semaphore semaphore;

  /**
   * components that haven't yet been started
   */
  private final Set<StepRunner> pendingSteps = new HashSet<StepRunner>();
  /**
   * components that have been started and not yet finished
   */
  private final Set<StepRunner> runningSteps = new HashSet<StepRunner>();
  /**
   * started and completed successfully
   */
  private final Set<StepRunner> completedSteps = new HashSet<StepRunner>();

  private final MultiShutdownHook shutdownHook;

  private boolean alreadyRun;
  private final CounterFilter counterFilter;
  private final ResourceManager<?, ?> resourceManager;
  private final TrackerURLBuilder trackerURLBuilder;


  public WorkflowRunner(Class klass, Step tail) throws IOException {
    this(klass, new DbPersistenceFactory(), tail);
  }

  public WorkflowRunner(Class klass, WorkflowOptions options, Step tail) throws IOException {
    this(klass, new DbPersistenceFactory(), options, tail);
  }

  public WorkflowRunner(Class klass, Set<Step> tailSteps) throws IOException {
    this(klass, new DbPersistenceFactory(), tailSteps);
  }

  public WorkflowRunner(Class klass, WorkflowOptions options, Set<Step> tailSteps) throws IOException {
    this(klass, new DbPersistenceFactory(), options, tailSteps);
  }

  // This constructor requires that the given options contain an AppType for generating the workflow name
  public WorkflowRunner(WorkflowOptions options, Step tail) throws IOException {
    this(new DbPersistenceFactory(), options, tail);
  }

  // This constructor requires that the given options contain an AppType for generating the workflow name
  public WorkflowRunner(WorkflowOptions options, Set<Step> tailSteps) throws IOException {
    this(new DbPersistenceFactory(), options, tailSteps);
  }


  public WorkflowRunner(String workflowName, WorkflowPersistenceFactory persistence, WorkflowOptions options, Step tail) throws IOException {
    this(workflowName, persistence, options, Sets.newHashSet(tail));
  }

  public WorkflowRunner(Class klass, WorkflowPersistenceFactory persistence, Step tail) throws IOException {
    this(klass.getName(), persistence, tail);
  }

  public WorkflowRunner(Class klass, WorkflowPersistenceFactory persistence, WorkflowOptions options, final Step first, Step... rest) throws IOException {
    this(klass.getName(), persistence, options, combine(first, rest));
  }

  public WorkflowRunner(Class klass, WorkflowPersistenceFactory persistence, WorkflowOptions options, Set<Step> tailSteps) throws IOException {
    this(klass.getName(), persistence, options, tailSteps);
  }

  public WorkflowRunner(Class klass, WorkflowPersistenceFactory persistence, Set<Step> tailSteps) throws IOException {
    this(klass, persistence, new ProductionWorkflowOptions(), tailSteps);
  }

  // This constructor requires that the given options contain an AppType for generating the workflow name
  public WorkflowRunner(WorkflowPersistenceFactory persistence, WorkflowOptions options, Set<Step> tailSteps) throws IOException {
    this(persistence.initialize(options), tailSteps);
  }

  // This constructor requires that the given options contain an AppType for generating the workflow name
  public WorkflowRunner(WorkflowPersistenceFactory persistence, WorkflowOptions options, Step tail) throws IOException {
    this(persistence, options, Sets.newHashSet(tail));
  }

  public WorkflowRunner(String workflowName, WorkflowPersistenceFactory persistence, Step tail) throws IOException {
    this(workflowName, persistence, Sets.newHashSet(tail));
  }

  public WorkflowRunner(String workflowName, WorkflowPersistenceFactory persistence, Set<Step> tail) throws IOException {
    this(workflowName, persistence, new ProductionWorkflowOptions(), Sets.newHashSet(tail));
  }

  public WorkflowRunner(String workflowName, WorkflowPersistenceFactory persistenceFactory, WorkflowOptions options, Set<Step> tailSteps) throws IOException {
    this(persistenceFactory.initialize(workflowName, options), tailSteps);
  }

  public WorkflowRunner(InitializedWorkflow initializedData, Step tail) throws IOException {
    this(initializedData, Sets.newHashSet(tail));
  }

  public WorkflowRunner(InitializedWorkflow initializedData, Set<Step> tailSteps) throws IOException {

    WorkflowOptions options = initializedData.getOptions();

    this.maxConcurrentSteps = options.getMaxConcurrentSteps();
    this.counterFilter = options.getCounterFilter();
    this.semaphore = new Semaphore(maxConcurrentSteps);
    this.lockProvider = options.getLockProvider().create();
    this.storage = options.getStorage();
    this.workflowJobProperties = options.getWorkflowJobProperties();
    this.stepPollInterval = options.getStepPollInterval();
    this.trackerURLBuilder = options.getUrlBuilder();

    WorkflowUtil.setCheckpointPrefixes(tailSteps);
    this.dependencyGraph = WorkflowDiagram.dependencyGraphFromTailSteps(tailSteps);

    assertSandbox(options.getSandboxDir());

    this.resourceManager = initializedData.getManager();
    this.persistence = initializedData.prepare(dependencyGraph);

    removeRedundantEdges(dependencyGraph);
    setStepContextObjects(dependencyGraph);

    // TODO: verify datasources satisfied

    // prep runners
    for (Step step : dependencyGraph.vertexSet()) {
      StepRunner runner = new StepRunner(step, this.persistence);
      pendingSteps.add(runner);
    }

    this.shutdownHook = initializedData.getShutdownHook();
    this.shutdownHook.add(new MultiShutdownHook.Hook() {
      @Override
      public void onShutdown() throws Exception {
        LOG.info("Marking running steps as failed");
        for (StepRunner runningStep : runningSteps) {
          persistence.markStepFailed(
              runningStep.step.getCheckpointToken(),
              new RuntimeException("Workflow process killed!")
          );
        }
      }
    });

  }

  private void setStepContextObjects(DirectedGraph<Step, DefaultEdge> dependencyGraph) {
    for (Step step : dependencyGraph.vertexSet()) {
      step.getAction().setOptionObjects(
          this.lockProvider,
          this.persistence,
          this.storage,
          this.counterFilter,
          this.resourceManager
      );
    }
  }

  private static HashSet<Step> combine(final Step first, Step... rest) {
    HashSet<Step> s = new HashSet<Step>(Arrays.asList(rest));
    s.add(first);
    return s;
  }

  private void removeRedundantEdges(DirectedGraph<Step, DefaultEdge> graph) {
    for (Step step : graph.vertexSet()) {
      Set<Step> firstDegDeps = new HashSet<Step>();
      Set<Step> secondPlusDegDeps = new HashSet<Step>();
      for (DefaultEdge edge : graph.outgoingEdgesOf(step)) {
        Step depStep = graph.getEdgeTarget(edge);
        firstDegDeps.add(depStep);
        getDepsRecursive(depStep, secondPlusDegDeps, graph);
      }

      for (Step firstDegDep : firstDegDeps) {
        if (secondPlusDegDeps.contains(firstDegDep)) {
          LOG.debug("Found a redundant edge from " + step.getCheckpointToken()
              + " to " + firstDegDep.getCheckpointToken());
          graph.removeAllEdges(step, firstDegDep);
        }
      }
    }
  }

  private void getDepsRecursive(Step step, Set<Step> deps, DirectedGraph<Step, DefaultEdge> graph) {
    for (DefaultEdge edge : graph.outgoingEdgesOf(step)) {
      Step s = graph.getEdgeTarget(edge);
      boolean isNew = deps.add(s);
      if (isNew) {
        getDepsRecursive(s, deps, graph);
      }
    }
  }

  private static String canonicalPath(String path) throws IOException {
    return new File(path).getCanonicalPath();
  }

  private static boolean isSubPath(String parentPath, String childPath) throws IOException {
    return canonicalPath(childPath).startsWith(canonicalPath(parentPath));
  }

  public WorkflowStatePersistence getPersistence() {
    return persistence;
  }

  private void checkStepsSandboxViolation(Set<DataStore> dataStores, String sandboxDir) throws IOException {
    if (dataStores != null) {
      for (DataStore dataStore : dataStores) {
        if (!isSubPath(sandboxDir, dataStore.getPath())) {
          throw new IOException("Step wants to write outside of sandbox \""
              + sandboxDir + "\"" + " into \"" + dataStore.getPath() + "\"");
        }
      }
    }
  }

  private void assertSandbox(String sandboxDir) {
    if (sandboxDir != null) {
      LOG.info("Checking that no action writes outside sandboxDir \"" + sandboxDir + "\"");
      try {
        for (Step step : getPhsyicalDependencyGraph().vertexSet()) {
          Action stepAction = step.getAction();
          if (stepAction != null) { // TODO: check if this check is necessary, it shouldn't be
            checkStepsSandboxViolation(stepAction.getDatastores(DSAction.CREATES), sandboxDir);
            checkStepsSandboxViolation(stepAction.getDatastores(DSAction.CREATES_TEMPORARY), sandboxDir);
          }
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Execute the workflow.
   *
   * @throws IOException
   */

  public synchronized void run() throws IOException {
    if (alreadyRun) {
      throw new IllegalStateException("The workflow is already running (or finished)!");
    }
    alreadyRun = true;
    try {

      // Notify
      LOG.info(getStartSubject());
      // Note: start email after web server so that UI is functional
      sendStartEmail();

      persistence.markWorkflowStarted();

      // Run internal
      runInternal();

      // Notify success
      sendSuccessEmail();
      LOG.info(getSuccessSubject());
    } finally {
      LOG.info("Removing shutdown hook");
      Runtime.getRuntime().removeShutdownHook(shutdownHook);
      LOG.info("Shutting down lock provider");
      lockProvider.shutdown();
      LOG.info("Timing statistics:\n" + TimeFormatting.getFormattedTimes(dependencyGraph, persistence));
    }
  }

  private void doRunLoop() throws IOException {
    try {
      while (pendingSteps.size() > 0 && shouldKeepStartingSteps() && existUnblockedSteps()) {
        // process any completed/failed steps
        clearFinishedSteps();

        // acquire semaphore so we don't do any polling until there are free permits
        semaphore.acquire();

        //  release the permit so it can be taken by a step
        semaphore.release();

        // check if there are any startable steps
        List<StepRunner> startableSteps = getStartableSteps();

        //  start each of them.  if we block for a while waiting for a free permit, that's fine
        for (StepRunner startableStep : startableSteps) {

          semaphore.acquire();

          //  we only check for shutdown requests here because we know that we do have a runnable step already
          if (!shouldKeepStartingSteps()) {
            LOG.info("Exiting early because of internal error or shutdown request");
            semaphore.release();
            return;
          }

          // start one startable
          runningSteps.add(startableStep);
          pendingSteps.remove(startableStep);
          startStep(startableStep);

          // note that we explicitly don't release the semaphore here. this is
          // because the actual step runner thread will release it when it's
          // complete (or failed).

        }

        //  if there was nothing to do this time, block for a while before trying again
        //  otherwise, we may have blocked for a while, and now want to poll immediately
        if (startableSteps.isEmpty()) {
          Thread.sleep(stepPollInterval);
        }
      }
    } catch (InterruptedException e) {
      LOG.debug("Interrupted waiting to acquire semaphore.", e);
    }


  }

  private void runInternal() throws IOException {

    // keep trying to start new components for as long as we are allowed and
    // there are components left to start
    doRunLoop();

    // acquire all the permits on the semaphore. this will guarantee that zero
    // components are running.
    try {
      semaphore.acquire(maxConcurrentSteps);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted waiting for running steps to complete!", e);
    }

    // there are now no running steps. clear the finished ones again in
    // case someone failed.
    clearFinishedSteps();

    persistence.markWorkflowStopped();

    // if there are any failures, then the workflow failed. throw an exception.
    if (isFailPending()) {
      String failureMessage = buildStepsFailureMessage();
      sendFailureEmail(failureMessage);
      throw new RuntimeException(getFailureSubject() + "\n" + failureMessage);
    }

    //  something internal to WorkflowRunner failed.
    if (!internalErrors.isEmpty()) {
      LOG.error("WorkflowRunner has encountered an internal error");
      sendInternalErrorMessage();
      throw new RuntimeException(getFailureSubject() + " internal WorkflowRunner error");
    }

    // nothing failed, but if there are steps that haven't been executed, it's
    // because someone shut down the workflow.
    if (pendingSteps.size() > 0) {
      String reason = getReasonForShutdownRequest();
      sendShutdownEmail(reason);
      throw new RuntimeException(getShutdownSubject(reason));
    }
  }

  private void sendInternalErrorMessage() throws IOException {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);

    pw.write("WorkflowRunner failed with an internal error.  Manual cleanup may be necessary.");

    for (Exception error : internalErrors) {
      pw.append(error.getMessage())
          .append("\n");
      error.printStackTrace(pw);
      pw.append("---------------------\n");
    }

    mail(getFailureSubject(), pw.toString(), WorkflowRunnerNotification.INTERNAL_ERROR);

  }

  private String buildStepFailureMessage(String step) throws IOException {
    return "Workflow will continue running non-blocked steps \n\n Step "
        + step + " failed with exception: "
        + persistence.getStepStates().get(step).getFailureMessage();
  }

  private String buildStepsFailureMessage() throws IOException {
    int n = 1;
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);

    int numFailed = 0;
    Map<String, StepState> statuses = persistence.getStepStates();
    for (Map.Entry<String, StepState> status : statuses.entrySet()) {
      if (status.getValue().getStatus() == StepStatus.FAILED) {
        numFailed++;
      }
    }

    for (Map.Entry<String, StepState> status : statuses.entrySet()) {
      StepState value = status.getValue();
      if (value.getStatus() == StepStatus.FAILED) {
        pw.println("(" + n + "/" + numFailed + ") Step "
            + status.getKey() + " failed with exception: "
            + value.getFailureMessage());
        pw.println(value.getFailureTrace());
        n++;
      }
    }
    return sw.toString();
  }

  public boolean isFailPending() throws IOException {

    for (Map.Entry<String, StepStatus> entry : persistence.getStepStatuses().entrySet()) {
      if (entry.getValue() == StepStatus.FAILED) {
        return true;
      }
    }

    return false;
  }

  private void sendStartEmail() throws IOException {
    mail(getStartSubject(), WorkflowRunnerNotification.START);
  }

  private void sendSuccessEmail() throws IOException {
    mail(getSuccessSubject(), WorkflowRunnerNotification.SUCCESS);
  }

  private void sendStepFailureEmail(String msg) throws IOException {
    mail(getStepFailureSubject(), msg, WorkflowRunnerNotification.STEP_FAILURE);
  }

  private void sendFailureEmail(String msg) throws IOException {
    mail(getFailureSubject(), msg, WorkflowRunnerNotification.FAILURE);
  }

  private void sendShutdownEmail(String cause) throws IOException {
    mail(getShutdownSubject(cause), WorkflowRunnerNotification.SHUTDOWN);
  }

  private String getDisplayName() throws IOException {
    return persistence.getName() + (this.persistence.getScopeIdentifier() == null ? "" : " (" + this.persistence.getScopeIdentifier() + ")");
  }

  private String getStartSubject() throws IOException {
    return "Started: " + getDisplayName();
  }

  private String getSuccessSubject() throws IOException {
    return "Succeeded: " + getDisplayName();
  }

  private String getFailureSubject() throws IOException {
    return "Failed: " + getDisplayName();
  }

  private String getStepFailureSubject() throws IOException {
    return "Step has failed in: " + getDisplayName();
  }

  private String getShutdownSubject(String reason) throws IOException {
    return "Shutdown requested: " + getDisplayName() + ". Reason: " + reason;
  }

  private void mail(String subject, WorkflowRunnerNotification notification) throws IOException {
    mail(subject, "", notification);
  }

  private void mail(String subject, String body, WorkflowRunnerNotification notification) throws IOException {
    for (AlertsHandler handler : persistence.getRecipients(notification)) {

      try {

        AlertMessages.Builder builder = AlertMessages.builder(subject)
            .setBody(appendTrackerUrl(body))
            .addToDefaultTags(WorkflowConstants.WORKFLOW_EMAIL_SUBJECT_TAG);

        if (notification.serverity() == AlertSeverity.ERROR) {
          builder.addToDefaultTags(WorkflowConstants.ERROR_EMAIL_SUBJECT_TAG);
        }

        handler.sendAlert(
            builder.build(),
            AlertRecipients.engineering(notification.serverity())
        );

      } catch (Exception e) {
        LOG.error("Failed to notify AlertsHandler " + handler, e);
      }

    }
  }

  private String appendTrackerUrl(String messageBody) throws IOException {
    return "Tracker URL: " + getTrackerURL() + "<br><br>" + messageBody;
  }

  public String getTrackerURL() throws IOException {
    return trackerURLBuilder.buildURL(persistence);
  }

  private String getReasonForShutdownRequest() throws IOException {
    return persistence.getShutdownRequest();
  }

  private void clearFinishedSteps() throws IOException {
    Iterator<StepRunner> iter = runningSteps.iterator();
    while (iter.hasNext()) {
      StepRunner cr = iter.next();
      //LOG.info("Checking persistence for " + cr.step.getCheckpointToken());
      switch (persistence.getStatus(cr.step.getCheckpointToken())) {
        case COMPLETED:
        case SKIPPED:
          completedSteps.add(cr);
          iter.remove();
          break;
        case FAILED:
          iter.remove();
          break;
      }
    }
  }

  private void startStep(StepRunner stepRunner) {
    stepRunner.start();
  }

  private boolean existUnblockedSteps() throws IOException {

    Queue<Step> explore = Lists.newLinkedList();
    Set<String> blockedSteps = Sets.newHashSet();

    Map<String, StepStatus> allStatuses = persistence.getStepStatuses();

    //  get failed steps
    for (Step step : dependencyGraph.vertexSet()) {
      if (allStatuses.get(step.getCheckpointToken()) == StepStatus.FAILED) {
        explore.add(step);
      }
    }

    //  get any part of the graph depending on failed steps
    while (!explore.isEmpty()) {
      Step step = explore.poll();
      if (!blockedSteps.contains(step.getCheckpointToken())) {
        blockedSteps.add(step.getCheckpointToken());
        for (DefaultEdge edge : dependencyGraph.incomingEdgesOf(step)) {
          explore.add(dependencyGraph.getEdgeSource(edge));
        }
      }
    }

    //  if any pending steps are not in this set, they can still plausibly run
    for (StepRunner pendingStep : pendingSteps) {
      if (!blockedSteps.contains(pendingStep.getStepName())) {
        return true;
      }
    }

    return false;

  }

  private List<StepRunner> getStartableSteps() throws IOException {

    Map<String, StepStatus> stepStatuses = persistence.getStepStatuses();

    List<StepRunner> allStartable = Lists.newArrayList();
    for (StepRunner cr : pendingSteps) {
      if (cr.allDependenciesCompleted(stepStatuses)) {
        allStartable.add(cr);
      }
    }
    return allStartable;
  }

  private boolean shouldKeepStartingSteps() throws IOException {
    return persistence.getShutdownRequest() == null && internalErrors.isEmpty();
  }

  public DirectedGraph<Step, DefaultEdge> getPhsyicalDependencyGraph() {
    return dependencyGraph;
  }

  /**
   * StepRunner keeps track of some extra state for each component, as
   * well as manages the actual execution thread. Note that it is itself *not*
   * a Thread.
   */
  private final class StepRunner {
    public final Step step;
    private final WorkflowStatePersistence state;
    public Thread thread;

    public StepRunner(Step c, WorkflowStatePersistence state) {
      this.step = c;
      this.state = state;
    }

    private OverridableProperties buildInheritedProperties() throws IOException {
      NestedProperties.Builder uiPropertiesBuilder = new NestedProperties.Builder();
      String priority = persistence.getPriority();
      String pool = persistence.getPool();

      if (priority != null) {
        uiPropertiesBuilder.setProperty(JOB_PRIORITY_PARAM, priority);
      }

      if (pool != null) {
        uiPropertiesBuilder.setProperty(JOB_POOL_PARAM, pool);
      }

      return uiPropertiesBuilder.build().override(workflowJobProperties);
    }

    public void start() {
      CascadingHelper.get().getJobConf();
      Runnable r = new Runnable() {
        @Override
        public void run() {
          String stepToken = step.getCheckpointToken();
          try {
            if (WorkflowEnums.NON_BLOCKING_STEP_STATUSES.contains(state.getStatus(stepToken))) {
              LOG.info("Step " + stepToken + " was executed successfully in a prior run. Skipping.");
            } else {

              persistence.markStepRunning(stepToken);

              LOG.info("Executing step " + stepToken);
              step.run(buildInheritedProperties());

              persistence.markStepCompleted(stepToken);
            }
          } catch (Throwable e) {

            LOG.error("Step " + stepToken + " failed!", e);

            try {
              persistence.markStepFailed(stepToken, e);

              //  only alert about this specific step failure if we aren't about to fail
              if (existUnblockedSteps() || runningSteps.size() > 1) {
                sendStepFailureEmail(buildStepFailureMessage(stepToken));
              }

            } catch (Exception e2) {
              LOG.error("Could not update step " + stepToken + " to failed! ", e2);
              internalErrors.add(e2);
            }


          } finally {
            semaphore.release();
          }
        }
      };
      thread = new Thread(r, "Step Runner for " + step.getCheckpointToken());
      thread.start();
    }

    public String getStepName() {
      return step.getCheckpointToken();
    }

    public boolean allDependenciesCompleted(Map<String, StepStatus> statuses) throws IOException {
      for (DefaultEdge edge : dependencyGraph.outgoingEdgesOf(step)) {
        Step dep = dependencyGraph.getEdgeTarget(edge);
        if (!WorkflowEnums.NON_BLOCKING_STEP_STATUSES.contains(statuses.get(dep.getCheckpointToken()))) {
          return false;
        }
      }
      return true;
    }
  }
}
