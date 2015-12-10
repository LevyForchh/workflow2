package com.liveramp.workflow_state;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.collections.comparators.ReverseComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.liveramp.commons.Accessors;
import com.liveramp.commons.collections.map.NestedMultimap;
import com.liveramp.commons.collections.nested_map.ThreeNestedMap;
import com.liveramp.commons.collections.nested_map.TwoNestedCountingMap;
import com.liveramp.commons.collections.nested_map.TwoNestedMap;
import com.liveramp.importer.generated.AppType;
import com.rapleaf.db_schemas.IDatabases;
import com.rapleaf.db_schemas.rldb.IRlDb;
import com.rapleaf.db_schemas.rldb.models.Application;
import com.rapleaf.db_schemas.rldb.models.ApplicationConfiguredNotification;
import com.rapleaf.db_schemas.rldb.models.ConfiguredNotification;
import com.rapleaf.db_schemas.rldb.models.MapreduceCounter;
import com.rapleaf.db_schemas.rldb.models.MapreduceJob;
import com.rapleaf.db_schemas.rldb.models.StepAttempt;
import com.rapleaf.db_schemas.rldb.models.StepAttemptDatastore;
import com.rapleaf.db_schemas.rldb.models.StepDependency;
import com.rapleaf.db_schemas.rldb.models.WorkflowAttempt;
import com.rapleaf.db_schemas.rldb.models.WorkflowAttemptConfiguredNotification;
import com.rapleaf.db_schemas.rldb.models.WorkflowAttemptDatastore;
import com.rapleaf.db_schemas.rldb.models.WorkflowExecution;
import com.rapleaf.db_schemas.rldb.models.WorkflowExecutionConfiguredNotification;
import com.rapleaf.db_schemas.rldb.util.JackUtil;
import com.rapleaf.jack.queries.Column;
import com.rapleaf.jack.queries.GenericQuery;
import com.rapleaf.jack.queries.QueryOrder;
import com.rapleaf.jack.queries.Record;
import com.rapleaf.jack.queries.Records;
import com.rapleaf.jack.queries.where_operators.In;
import com.rapleaf.jack.queries.where_operators.IsNull;

import static com.rapleaf.jack.queries.Functions.DATETIME;

public class WorkflowQueries {
  private static final Logger LOG = LoggerFactory.getLogger(WorkflowQueries.class);

  public static WorkflowExecution getLatestExecution(IRlDb rldb, String name, String scopeIdentifier) throws IOException {
    Records records = rldb.createQuery()
        .from(Application.TBL)
        .innerJoin(WorkflowExecution.TBL)
        .on(Application.ID.equalTo(WorkflowExecution.APPLICATION_ID.as(Long.class)))
        .where(Application.NAME.equalTo(name))
        .where(WorkflowExecution.SCOPE_IDENTIFIER.equalTo(scopeIdentifier))
        .orderBy(WorkflowExecution.ID, QueryOrder.DESC)
        .select(WorkflowExecution.TBL.getAllColumns())
        .limit(1)
        .fetch();

    if (records.isEmpty()) {
      return null;
    }

    return JackUtil.getFullModel(WorkflowExecution.class, WorkflowExecution.Attributes.class, Accessors.only(records), rldb.getDatabases());
  }

  public static Optional<WorkflowExecution> getLatestExecution(IRlDb rldb, AppType type, String scopeIdentifier) throws IOException {
    Records records = rldb.createQuery()
        .from(Application.TBL)
        .innerJoin(WorkflowExecution.TBL)
        .on(Application.ID.equalTo(WorkflowExecution.APPLICATION_ID.as(Long.class)))
        .where(Application.APP_TYPE.equalTo(type.getValue()))
        .where(WorkflowExecution.SCOPE_IDENTIFIER.equalTo(scopeIdentifier))
        .orderBy(WorkflowExecution.ID, QueryOrder.DESC)
        .select(WorkflowExecution.TBL.getAllColumns())
        .limit(1)
        .fetch();

    if (records.isEmpty()) {
      return Optional.absent();
    }

    return Optional.of(JackUtil.getFullModel(WorkflowExecution.class, WorkflowExecution.Attributes.class, Accessors.only(records), rldb.getDatabases()));
  }

  //  TODO temporary for some scripts while stuff is getting migrated
  public static boolean hasExecution(IRlDb rldb, AppType type, String scopeIdentifier) throws IOException {
    return !rldb.createQuery()
        .from(Application.TBL)
        .innerJoin(WorkflowExecution.TBL)
        .on(Application.ID.equalTo(WorkflowExecution.APPLICATION_ID.as(Long.class)))
        .where(Application.APP_TYPE.equalTo(type.getValue()))
        .where(WorkflowExecution.SCOPE_IDENTIFIER.equalTo(scopeIdentifier))
        .orderBy(WorkflowExecution.ID, QueryOrder.DESC)
        .limit(1)
        .fetch().isEmpty();
  }

  //  TODO temporary for some scripts while stuff is getting migrated
  public static boolean hasExecution(IRlDb rldb, String name, String scopeIdentifier) throws IOException {
    return !rldb.createQuery()
        .from(Application.TBL)
        .innerJoin(WorkflowExecution.TBL)
        .on(Application.ID.equalTo(WorkflowExecution.APPLICATION_ID.as(Long.class)))
        .where(Application.NAME.equalTo(name))
        .where(WorkflowExecution.SCOPE_IDENTIFIER.equalTo(scopeIdentifier))
        .orderBy(WorkflowExecution.ID, QueryOrder.DESC)
        .limit(1)
        .fetch().isEmpty();
  }

  public static WorkflowExecutionStatus getLatestExecutionStatus(IRlDb rlDb, AppType appType, String scopeIdentifier) throws IOException {
    Optional<WorkflowExecution> execution = WorkflowQueries.getLatestExecution(rlDb, appType, scopeIdentifier);
    if (execution.isPresent()) {
      return WorkflowExecutionStatus.findByValue(execution.get().getStatus());
    } else {
      throw new IllegalStateException("No executions present for the supplied app and scope id");
    }
  }

  public static WorkflowExecutionStatus getLatestExecutionStatus(IRlDb rlDb, String name, String scopeIdentifier) throws IOException {
    WorkflowExecution execution = WorkflowQueries.getLatestExecution(rlDb, name, scopeIdentifier);
    if (execution == null) {
      return null;
    }
    return WorkflowExecutionStatus.findByValue(execution.getStatus());
  }

  public static boolean isStepComplete(String step, WorkflowExecution execution) throws IOException {
    return getCompletedStep(step, execution) != null;

  }

  public static StepAttempt getCompletedStep(String step, WorkflowExecution execution) throws IOException {

    Set<StepAttempt> matches = Sets.newHashSet();

    for (WorkflowAttempt attempts : execution.getWorkflowAttempt()) {
      for (StepAttempt stepAttempt : attempts.getStepAttempt()) {
        if (stepAttempt.getStepToken().equals(step) && stepAttempt.getStepStatus() == StepStatus.COMPLETED.ordinal()) {
          matches.add(stepAttempt);
        }
      }
    }

    if (matches.size() > 1) {
      throw new RuntimeException("Found multiple complete step attempts for a workflow attempt!");
    }

    if (matches.isEmpty()) {
      return null;
    }

    return Accessors.first(matches);

  }

  public static TwoNestedMap<String, String, Long> countersAsMap(Collection<MapreduceCounter> counters) {
    TwoNestedMap<String, String, Long> asMap = new TwoNestedMap<>();
    for (MapreduceCounter counter : counters) {
      asMap.put(counter.getGroup(), counter.getName(), counter.getValue());
    }
    return asMap;
  }

  public static Optional<WorkflowAttempt> getLatestAttemptOptional(WorkflowExecution execution) throws IOException {
    return getLatestAttemptOptional(execution.getWorkflowAttempt());
  }

  public static Optional<WorkflowAttempt> getLatestAttemptOptional(Collection<WorkflowAttempt> attempts) throws IOException {
    return Accessors.firstOrAbsent(getAttemptsDescending(attempts));
  }

  public static WorkflowAttempt getLatestAttempt(WorkflowExecution execution) throws IOException {
    return Accessors.first(getAttemptsDescending(execution.getWorkflowAttempt()));
  }

  public static List<WorkflowAttempt> getAttemptsDescending(Collection<WorkflowAttempt> attempts) throws IOException {

    List<WorkflowAttempt> attemptsList = Lists.newArrayList(attempts);
    Collections.sort(attemptsList, new ReverseComparator());

    return attemptsList;
  }

  public static String getPool(WorkflowAttempt attempt, WorkflowExecution execution) {

    String poolOverride = execution.getPoolOverride();
    if (poolOverride != null) {
      return poolOverride;
    }

    return attempt.getPool();
  }

  public static ProcessStatus getProcessStatus(WorkflowAttempt attempt, WorkflowExecution execution) throws IOException {
    return getProcessStatus(attempt, execution, DbPersistence.NUM_HEARTBEAT_TIMEOUTS);
  }

  //  it's dumb to require both but it lets us avoid extra db lookups
  public static ProcessStatus getProcessStatus(WorkflowAttempt attempt, WorkflowExecution execution, int missedHeartbeatsThreshold) throws IOException {

    Long lastHeartbeat = attempt.getLastHeartbeat();

    Integer status = attempt.getStatus();

    if (!AttemptStatus.LIVE_STATUSES.contains(status)) {
      return ProcessStatus.STOPPED;
    }

    //  assume dead (OOME killed, etc) if no heartbeat for 4x interval
    if (System.currentTimeMillis() - lastHeartbeat >
        missedHeartbeatsThreshold * DbPersistence.HEARTBEAT_INTERVAL) {

      //  let manual cleanup get rid of the timeout status
      if (execution.getStatus() == WorkflowExecutionStatus.CANCELLED.ordinal()) {
        return ProcessStatus.STOPPED;
      }

      return ProcessStatus.TIMED_OUT;
    }

    return ProcessStatus.ALIVE;

  }

  public static List<WorkflowExecution> getDiedUncleanExecutions(IDatabases databases, AppType app, long windowDays, int missedHeartbeatsThreshold) throws IOException {

    List<WorkflowExecution> executions = WorkflowQueries.queryWorkflowExecutions(databases, null, null, null, null,
        System.currentTimeMillis() - windowDays * 24 * 60 * 60 * 1000, null, WorkflowExecutionStatus.INCOMPLETE, null
    );

    List<WorkflowExecution> dead = Lists.newArrayList();

    for (WorkflowExecution execution : executions) {
      Optional<WorkflowAttempt> attemptOptional = WorkflowQueries.getLatestAttemptOptional(execution);

      if (attemptOptional.isPresent()) {
        if (WorkflowQueries.getProcessStatus(attemptOptional.get(), execution, missedHeartbeatsThreshold) == ProcessStatus.TIMED_OUT) {
          if (app == null || execution.getApplication().getAppType().equals(app.getValue())) {
            dead.add(execution);
          }
        }
      }

    }

    return dead;
  }

  public static boolean workflowComplete(WorkflowExecution workflowExecution) throws IOException {

    for (StepAttempt attempt : getLatestAttempt(workflowExecution).getStepAttempt()) {
      if (!isStepComplete(attempt.getStepToken(), workflowExecution)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isLatestExecution(IRlDb rldb, WorkflowExecution execution) throws IOException {

    WorkflowExecution latestExecution = getLatestExecution(rldb,
        execution.getName(),
        execution.getScopeIdentifier()
    );

    if (latestExecution == null) {
      return true;
    }

    return latestExecution.getId() == execution.getId();
  }

  //  either steps or cancel
  public static boolean canRevert(IRlDb rldb, WorkflowExecution execution) throws IOException {

    if (!WorkflowQueries.isLatestExecution(rldb, execution)) {
      LOG.info("Execution is not latest");
      return false;
    }

    if (execution.getStatus() == WorkflowExecutionStatus.CANCELLED.ordinal()) {
      LOG.info("Execution is already cancelled");
      return false;
    }

    if (WorkflowQueries.getProcessStatus(getLatestAttempt(execution), execution) == ProcessStatus.ALIVE) {
      LOG.info("Process is still alive");
      return false;
    }

    return true;
  }

  public static ThreeNestedMap<String, String, String, Long> getCountersByStep(IRlDb rldb, Long workflowExecution) throws IOException {
    ThreeNestedMap<String, String, String, Long> counters = new ThreeNestedMap<>();

    for (Record record : getCompleteStepCounters(rldb, workflowExecution).select(StepAttempt.STEP_TOKEN, MapreduceCounter.GROUP, MapreduceCounter.NAME, MapreduceCounter.VALUE)
        .fetch()) {
      counters.put(record.getString(StepAttempt.STEP_TOKEN),
          record.getString(MapreduceCounter.GROUP),
          record.getString(MapreduceCounter.NAME),
          record.getLong(MapreduceCounter.VALUE)
      );
    }

    return counters;
  }

  public static TwoNestedMap<String, String, Long> getFlatCounters(IRlDb rldb, Long workflowExecution) throws IOException {
    TwoNestedCountingMap<String, String> counters = new TwoNestedCountingMap<>(0l);

    for (Record record : getCompleteStepCounters(rldb, workflowExecution).select(MapreduceCounter.GROUP, MapreduceCounter.NAME, MapreduceCounter.VALUE)
        .fetch()) {
      counters.incrementAndGet(record.getString(MapreduceCounter.GROUP),
          record.getString(MapreduceCounter.NAME),
          record.getLong(MapreduceCounter.VALUE)
      );
    }

    return counters;
  }

  public static GenericQuery completeMapreduceJobQuery(IDatabases databases,
                                                       Long endedAfter,
                                                       Long endedBefore) {

    GenericQuery stepAttempts = databases.getRlDb().createQuery().from(StepAttempt.TBL)
        .where(StepAttempt.END_TIME.isNotNull())
        .where(StepAttempt.STEP_STATUS.equalTo(StepStatus.COMPLETED.ordinal()));

    if (endedAfter != null) {
      stepAttempts = stepAttempts.where(StepAttempt.END_TIME.greaterThan(DATETIME(endedAfter)));
    }
    if (endedBefore != null) {
      stepAttempts = stepAttempts.where(StepAttempt.END_TIME.lessThanOrEqualTo(DATETIME(endedBefore)));
    }

    return stepAttempts.innerJoin(MapreduceJob.TBL)
        .on(MapreduceJob.STEP_ATTEMPT_ID.equalTo(StepAttempt.ID.as(Integer.class)));

  }

  public static List<MapreduceJob> getCompleteMapreduceJobs(IDatabases databases,
                                                            Long endedAfter,
                                                            Long endedBefore) throws IOException {

    List<MapreduceJob> jobs = Lists.newArrayList();
    for (Record record : completeMapreduceJobQuery(databases,
        endedAfter, endedBefore)
        .select(MapreduceJob.TBL.getAllColumns()).fetch()) {
      jobs.add(JackUtil.getFullModel(MapreduceJob.class, MapreduceJob.Attributes.class, record, databases));
    }

    return jobs;
  }

  public static Map<Long, Long> getStepAttemptIdtoWorkflowExecutionId(IDatabases databases,
                                                                      Collection<Long> stepAttemptIds) throws IOException {
    Map<Long, Long> map = Maps.newHashMap();
    for (Record record : stepAttemptToExecutionQuery(databases, stepAttemptIds)
        .select(StepAttempt.ID, WorkflowExecution.ID)
        .fetch()) {
      map.put(record.getLong(StepAttempt.ID), record.getLong(WorkflowExecution.ID));
    }
    return map;
  }

  private static GenericQuery stepAttemptToExecutionQuery(IDatabases databases, Collection<Long> stepAttemptIds) {
    return databases.getRlDb().createQuery().from(WorkflowExecution.TBL)
        .innerJoin(WorkflowAttempt.TBL)
        .on(WorkflowAttempt.WORKFLOW_EXECUTION_ID.equalTo(WorkflowExecution.ID.as(Integer.class)))
        .innerJoin(StepAttempt.TBL)
        .on(StepAttempt.WORKFLOW_ATTEMPT_ID.equalTo(WorkflowAttempt.ID.as(Integer.class)))
        .where(StepAttempt.ID.in(stepAttemptIds));
  }

  public static Set<WorkflowExecution> getExecutionsForStepAttempts(IDatabases databases,
                                                                    Collection<Long> stepAttemptIds) throws IOException {

    //  maybe a little conservative to distinct IDs first but some workflows have 123213214123131 steps so eh will reduce result size
    Set<Long> ids = Sets.newHashSet();
    for (Record record : stepAttemptToExecutionQuery(databases, stepAttemptIds)
        .select(WorkflowExecution.TBL.ID).fetch()) {
      ids.add(record.getLong(WorkflowExecution.TBL.ID));
    }

    Set<WorkflowExecution> executions = Sets.newHashSet();
    for (Record record : databases.getRlDb().createQuery().from(WorkflowExecution.TBL)
        .where(WorkflowExecution.ID.in(ids))
        .fetch()) {
      executions.add(JackUtil.getFullModel(WorkflowExecution.class, WorkflowExecution.Attributes.class, record, databases));
    }

    return executions;
  }

  public static List<MapreduceCounter> getAllJobCounters(IDatabases databases,
                                                         Long endedAfter,
                                                         Long endedBefore,
                                                         Set<String> group,
                                                         Set<String> name) throws IOException {

    GenericQuery counterQuery = completeMapreduceJobQuery(databases, endedAfter, endedBefore)
        .innerJoin(MapreduceCounter.TBL)
        .on(MapreduceCounter.MAPREDUCE_JOB_ID.equalTo(MapreduceJob.ID.as(Integer.class)));

    if (group != null) {
      counterQuery = counterQuery.where(MapreduceCounter.GROUP.in(group));
    }

    if (name != null) {
      counterQuery = counterQuery.where(MapreduceCounter.NAME.in(name));
    }

    List<MapreduceCounter> counters = Lists.newArrayList();
    for (Record record : counterQuery
        .select(MapreduceCounter.TBL.getAllColumns()).fetch()) {
      counters.add(JackUtil.getFullModel(MapreduceCounter.class, MapreduceCounter.Attributes.class, record, databases));
    }

    return counters;

  }

  public static List<WorkflowAttempt> getLiveWorkflowAttempts(IRlDb rldb,
                                                              Long executionId) throws IOException {

    List<WorkflowAttempt> attempts = rldb.workflowAttempts().query()
        .workflowExecutionId(executionId.intValue())
        .whereStatus(new In<>(AttemptStatus.LIVE_STATUSES))
        .find();

    attempts.addAll(rldb.workflowAttempts().query()
        .workflowExecutionId(executionId.intValue())
        .whereStatus(new IsNull<Integer>())
        .find()
    );

    return attempts;
  }

  public static List<Application> getAllApplications(IDatabases databases) throws IOException {
    return databases.getRlDb().applications().findAll();
  }

  public static NestedMultimap<Long, DSAction, WorkflowAttemptDatastore> getApplicationIdToWorkflowAttemptDatastores(IDatabases databases,
                                                                                                                     Long startedAfter,
                                                                                                                     Long startedBefore) throws IOException {

    Set<Column> columns = Sets.newHashSet(WorkflowAttemptDatastore.TBL.getAllColumns());
    columns.add(WorkflowExecution.APPLICATION_ID);
    columns.add(StepAttemptDatastore.DS_ACTION);

    GenericQuery on = joinStepAttempts(workflowExecutionQuery(databases.getRlDb(), null, null, startedAfter, startedBefore))
        .innerJoin(StepAttemptDatastore.TBL)
        .on(StepAttempt.ID.equalTo(StepAttemptDatastore.STEP_ATTEMPT_ID.as(Long.class)))
        .innerJoin(WorkflowAttemptDatastore.TBL)
        .on(StepAttemptDatastore.WORKFLOW_ATTEMPT_DATASTORE_ID.equalTo(WorkflowAttemptDatastore.ID.as(Integer.class)))
        .select(columns);

    NestedMultimap<Long, DSAction, WorkflowAttemptDatastore> stores = new NestedMultimap<>();
    for (Record record : on.fetch()) {

      Integer appId = record.getInt(WorkflowExecution.APPLICATION_ID);
      Integer dsAction = record.getInt(StepAttemptDatastore.DS_ACTION);
      WorkflowAttemptDatastore model = JackUtil.getFullModel(WorkflowAttemptDatastore.class, WorkflowAttemptDatastore.Attributes.class, record, databases);

      stores.put(appId.longValue(), DSAction.findByValue(dsAction), model);

    }

    return stores;

  }

  public static Multimap<WorkflowExecution, WorkflowAttempt> getExecutionsToAttempts(IDatabases databases,
                                                                                     Long id,
                                                                                     String name,
                                                                                     String scope,
                                                                                     Integer appType,
                                                                                     Long startedAfter,
                                                                                     Long startedBefore,
                                                                                     WorkflowExecutionStatus status,
                                                                                     Integer limit) throws IOException {

    List<WorkflowExecution> executions = queryWorkflowExecutions(databases, id, name, scope, appType, startedAfter, startedBefore, status, limit);

    Map<Long, WorkflowExecution> executionsById = Maps.newHashMap();
    for (WorkflowExecution execution : executions) {
      executionsById.put(execution.getId(), execution);
    }

    Multimap<WorkflowExecution, WorkflowAttempt> executionAttempts = HashMultimap.create();
    for (WorkflowAttempt attempt : getWorkflowAttempts(databases, executionsById.keySet())) {
      executionAttempts.put(executionsById.get((long)attempt.getWorkflowExecutionId()), attempt);
    }

    return executionAttempts;
  }

  public static List<ConfiguredNotification.Attributes> getAttemptNotifications(IRlDb rldb, WorkflowRunnerNotification type, Long attemptId) throws IOException {
    return getNotifications(rldb.createQuery().from(WorkflowAttemptConfiguredNotification.TBL)
            .where(WorkflowAttemptConfiguredNotification.WORKFLOW_ATTEMPT_ID.equalTo(attemptId))
            .innerJoin(ConfiguredNotification.TBL)
            .on(WorkflowAttemptConfiguredNotification.CONFIGURED_NOTIFICATION_ID.equalTo(ConfiguredNotification.ID)),
        type,
        null
    );
  }

  public static List<ConfiguredNotification.Attributes> getExecutionNotifications(IRlDb rldb, Long executionId) throws IOException {
    return getExecutionNotifications(rldb, executionId, null, null);
  }

  public static List<ConfiguredNotification.Attributes> getExecutionNotifications(IRlDb rldb, Long executionId, String email) throws IOException {
    return getExecutionNotifications(rldb, executionId, null, email);
  }

  public static List<ConfiguredNotification.Attributes> getExecutionNotifications(IRlDb rldb, Long executionId, WorkflowRunnerNotification type) throws IOException {
    return getExecutionNotifications(rldb, executionId, type, null);
  }

  public static List<ConfiguredNotification.Attributes> getExecutionNotifications(IRlDb rldb, Long executionId, WorkflowRunnerNotification type, String email) throws IOException {
    return getNotifications(rldb.createQuery().from(WorkflowExecutionConfiguredNotification.TBL)
            .where(WorkflowExecutionConfiguredNotification.WORKFLOW_EXECUTION_ID.equalTo(executionId))
            .innerJoin(ConfiguredNotification.TBL)
            .on(WorkflowExecutionConfiguredNotification.CONFIGURED_NOTIFICATION_ID.equalTo(ConfiguredNotification.ID)),
        type,
        email
    );
  }

  public static List<ConfiguredNotification.Attributes> getApplicationNotifications(IRlDb rldb, Long applicationId) throws IOException {
    return getApplicationNotifications(rldb, applicationId, null, null);
  }

  public static List<ConfiguredNotification.Attributes> getApplicationNotifications(IRlDb rldb, Long applicationId, WorkflowRunnerNotification type) throws IOException {
    return getApplicationNotifications(rldb, applicationId, type, null);
  }

  public static List<ConfiguredNotification.Attributes> getApplicationNotifications(IRlDb rldb, Long applicationId, String email) throws IOException {
    return getApplicationNotifications(rldb, applicationId, null, email);
  }

  public static List<ConfiguredNotification.Attributes> getApplicationNotifications(IRlDb rldb, Long applicationId, WorkflowRunnerNotification type, String email) throws IOException {
    return getNotifications(rldb.createQuery().from(ApplicationConfiguredNotification.TBL)
            .where(ApplicationConfiguredNotification.APPLICATION_ID.equalTo(applicationId))
            .innerJoin(ConfiguredNotification.TBL)
            .on(ApplicationConfiguredNotification.CONFIGURED_NOTIFICATION_ID.equalTo(ConfiguredNotification.ID)),
        type,
        email
    );
  }

  private static List<ConfiguredNotification.Attributes> getNotifications(GenericQuery configuredNotifications, WorkflowRunnerNotification type, String email) throws IOException {

    if (type != null) {
      configuredNotifications = configuredNotifications.where(ConfiguredNotification.WORKFLOW_RUNNER_NOTIFICATION.equalTo(type.ordinal()));
    }

    if (email != null) {
      configuredNotifications = configuredNotifications.where(ConfiguredNotification.EMAIL.equalTo(email));
    }

    List<ConfiguredNotification.Attributes> notifications = Lists.newArrayList();
    for (Record record : configuredNotifications
        .select(ConfiguredNotification.TBL.getAllColumns())
        .fetch()) {
      notifications.add(JackUtil.getModel(ConfiguredNotification.Attributes.class, record));
    }

    return notifications;
  }


  public static List<WorkflowAttempt> getWorkflowAttempts(IDatabases databases,
                                                          Long endedAfter,
                                                          Long endedBefore) throws IOException {

    List<WorkflowAttempt> workflowAttempts = Lists.newArrayList();
    for (Record record : databases.getRlDb().createQuery().from(WorkflowAttempt.TBL)
        .where(WorkflowAttempt.END_TIME.between(endedAfter, endedBefore))
        .fetch()) {
      workflowAttempts.add(JackUtil.getFullModel(WorkflowAttempt.class, WorkflowAttempt.Attributes.class, record, databases));
    }
    return workflowAttempts;

  }

  //  public static Map<Long, Multimap<DSAction, WorkflowAttemptDatastore>> getApplicationDSActions(IDatabases rldb, Long startedAfter, Long startedBefore) throws IOException {
  //
  //    GenericQuery genericQuery = workflowExecutionQuery(rldb.getRlDb(), null, null, startedAfter, startedBefore);
  //
  //    workflowAttemptquer
  //
  //
  //  }
  //
  //  private GenericQuery attemptDataStore

  public static List<WorkflowAttempt> getWorkflowAttempts(IDatabases databases,
                                                          Set<Long> workflowExecutionIds) throws IOException {

    List<WorkflowAttempt> workflowAttempts = Lists.newArrayList();
    for (Record record : databases.getRlDb().createQuery().from(WorkflowAttempt.TBL)
        .where(WorkflowAttempt.WORKFLOW_EXECUTION_ID.as(Long.class).in(workflowExecutionIds))
        .fetch()) {
      workflowAttempts.add(JackUtil.getFullModel(WorkflowAttempt.class, WorkflowAttempt.Attributes.class, record, databases));
    }
    return workflowAttempts;
  }

  public static List<WorkflowExecution> queryWorkflowExecutions(IDatabases databases,
                                                                String name,
                                                                Integer appType,
                                                                Long startedAfter,
                                                                Long startedBefore,
                                                                Integer limit) throws IOException {
    return queryWorkflowExecutions(databases, null, name, null, appType, startedAfter, startedBefore, null, limit);
  }

  public static List<WorkflowExecution> queryWorkflowExecutions(IDatabases databases,
                                                                Long id,
                                                                String name,
                                                                String scope,
                                                                Integer appType,
                                                                Long startedAfter,
                                                                Long startedBefore,
                                                                WorkflowExecutionStatus status,
                                                                Integer limit) throws IOException {
    Records fetch = workflowExecutionQuery(databases.getRlDb(), id, name, scope, appType, startedAfter, startedBefore, status, limit)
        .select(WorkflowExecution.TBL.getAllColumns())
        .fetch();

    List<WorkflowExecution> executions = Lists.newArrayList();

    for (Record record : fetch) {
      executions.add(new WorkflowExecution(JackUtil.getModel(WorkflowExecution.Attributes.class, record), databases));
    }

    return executions;
  }

  public static GenericQuery workflowExecutionQuery(IRlDb rldb, String name, Integer appType, Long startedAfter, Long startedBefore) throws IOException {
    return workflowExecutionQuery(rldb, null, name, null, appType, startedAfter, startedBefore, null, null);
  }

  public static GenericQuery workflowExecutionQuery(IRlDb rldb,
                                                    Long id,
                                                    String name,
                                                    String scope,
                                                    Integer appType,
                                                    Long startedAfter,
                                                    Long startedBefore,
                                                    WorkflowExecutionStatus status,
                                                    Integer limit) throws IOException {

    GenericQuery.Builder queryb = rldb.createQuery();
    GenericQuery query;

    if (name != null || appType != null) {

      query = queryb.from(Application.TBL);

      if (name != null) {
        query = query.where(Application.NAME.equalTo(name));
      }

      if (appType != null) {
        query.where(Application.APP_TYPE.equalTo(appType));
      }

      query.innerJoin(WorkflowExecution.TBL)
          .on(WorkflowExecution.APPLICATION_ID.equalTo(Application.ID.as(Integer.class)));

    } else {
      query = queryb.from(WorkflowExecution.TBL);
    }

    if (id != null) {
      query = query.where(WorkflowExecution.ID.equalTo(id));
    }

    if (scope != null) {
      query = query.where(WorkflowExecution.SCOPE_IDENTIFIER.equalTo(scope));
    }

    if (startedBefore != null) {
      query = query.where(WorkflowExecution.START_TIME.lessThan(DATETIME(startedBefore)));
    }

    if (startedAfter != null) {
      query = query.where(WorkflowExecution.START_TIME.greaterThan(DATETIME(startedAfter)));
    }

    if (status != null) {
      query = query.where(WorkflowExecution.STATUS.equalTo(status.ordinal()));
    }

    if (limit != null) {
      query = query.orderBy(WorkflowExecution.ID, QueryOrder.DESC);
      query = query.limit(limit);
    }

    return query;
  }

  public static List<StepAttempt.Attributes> getStepAttempts(IRlDb rldb, Long workflowAttemptId) throws IOException {
    List<StepAttempt.Attributes> executions = Lists.newArrayList();
    for (Record record : rldb.createQuery().from(StepAttempt.TBL).where(StepAttempt.WORKFLOW_ATTEMPT_ID.as(Long.class).equalTo(workflowAttemptId)).fetch()) {
      executions.add(JackUtil.getModel(StepAttempt.Attributes.class, record));
    }
    return executions;
  }

  public static List<StepDependency.Attributes> getStepDependencies(IRlDb rldb, Set<Long> stepAttemptIds) throws IOException {
    List<StepDependency.Attributes> dependencies = Lists.newArrayList();
    for (Record record : rldb.createQuery().from(StepDependency.TBL).where(StepDependency.STEP_ATTEMPT_ID.as(Long.class).in(stepAttemptIds).or(StepDependency.DEPENDENCY_ATTEMPT_ID.as(Long.class).in(stepAttemptIds))).fetch()) {
      dependencies.add(JackUtil.getModel(StepDependency.Attributes.class, record));
    }
    return dependencies;
  }

  public static List<MapreduceJob.Attributes> getMapreduceJobs(IRlDb rldb, Set<Long> stepAttemptIds) throws IOException {
    List<MapreduceJob.Attributes> jobs = Lists.newArrayList();
    for (Record record : rldb.createQuery().from(MapreduceJob.TBL).where(MapreduceJob.STEP_ATTEMPT_ID.as(Long.class).in(stepAttemptIds)).fetch()) {
      jobs.add(JackUtil.getModel(MapreduceJob.Attributes.class, record));
    }
    return jobs;
  }

  public static List<MapreduceCounter.Attributes> getMapreduceCounters(IRlDb rldb, Set<Long> mapreduceJobIds) throws IOException {
    List<MapreduceCounter.Attributes> counters = Lists.newArrayList();
    for (Record record : rldb.createQuery().from(MapreduceCounter.TBL).where(MapreduceCounter.MAPREDUCE_JOB_ID.as(Long.class).in(mapreduceJobIds)).fetch()) {
      counters.add(JackUtil.getModel(MapreduceCounter.Attributes.class, record));
    }
    return counters;
  }

  public static List<StepAttemptDatastore.Attributes> getStepAttemptDatastores(IRlDb rldb, Set<Long> stepIds) throws IOException {
    List<StepAttemptDatastore.Attributes> attemptDatastores = Lists.newArrayList();
    for (Record record : rldb.createQuery().from(StepAttemptDatastore.TBL).where(StepAttemptDatastore.STEP_ATTEMPT_ID.as(Long.class).in(stepIds)).fetch()) {
      attemptDatastores.add(JackUtil.getModel(StepAttemptDatastore.Attributes.class, record));
    }
    return attemptDatastores;
  }

  public static List<WorkflowAttemptDatastore.Attributes> getWorkflowAttemptDatastores(IRlDb rldb, Long workflowAttemptId) throws IOException {
    List<WorkflowAttemptDatastore.Attributes> workflowAttemptDatastore = Lists.newArrayList();
    for (Record record : rldb.createQuery().from(WorkflowAttemptDatastore.TBL).where(WorkflowAttemptDatastore.WORKFLOW_ATTEMPT_ID.as(Long.class).equalTo(workflowAttemptId)).fetch()) {
      workflowAttemptDatastore.add(JackUtil.getModel(WorkflowAttemptDatastore.Attributes.class, record));
    }
    return workflowAttemptDatastore;
  }

  //  join queries

  public static GenericQuery getStepAttempts(IRlDb rldb, Long execution, Set<String> latestTokens, EnumSet<StepStatus> statuses) {
    return filterStepAttempts(joinStepAttempts(rldb.createQuery().from(WorkflowExecution.TBL)
            .where(WorkflowExecution.ID.equalTo(execution))),
        latestTokens,
        statuses
    );
  }

  public static GenericQuery getCompleteStepCounters(IRlDb rldb, Long executionId) throws IOException {

    //  TODO can use one query for this whole thing probably
    WorkflowExecution execution = rldb.workflowExecutions().find(executionId);

    WorkflowAttempt latestAttempt = WorkflowQueries.getLatestAttempt(execution);
    List<StepAttempt> steps = latestAttempt.getStepAttempt();

    Set<String> latestTokens = Sets.newHashSet();
    for (StepAttempt attempt : steps) {
      latestTokens.add(attempt.getStepToken());
    }

    return rldb.createQuery()
        .from(StepAttempt.TBL)
        .where(StepAttempt.STEP_TOKEN.in(latestTokens))
        .where(StepAttempt.STEP_STATUS.equalTo(StepStatus.COMPLETED.ordinal()))
        .innerJoin(WorkflowAttempt.TBL)
        .on(StepAttempt.WORKFLOW_ATTEMPT_ID.equalTo(WorkflowAttempt.ID.as(Integer.class)))
        .where(WorkflowAttempt.WORKFLOW_EXECUTION_ID.as(Long.class).equalTo(executionId))
        .innerJoin(MapreduceJob.TBL)
        .on(MapreduceJob.STEP_ATTEMPT_ID.equalTo(StepAttempt.ID.as(Integer.class)))
        .innerJoin(MapreduceCounter.TBL)
        .on(MapreduceCounter.MAPREDUCE_JOB_ID.equalTo(MapreduceJob.ID.as(Integer.class)));

  }

  public static GenericQuery getMapreduceCounters(IRlDb rldb, Set<String> stepToken, String name, Integer appType, Long startedAfter, Long startedBefore,
                                                  Set<String> specificGroups,
                                                  Set<String> specificNames) throws IOException {
    return getMapreduceCounters(getStepAttempts(rldb, stepToken, name, appType, startedAfter, startedBefore), specificGroups, specificNames);
  }

  public static GenericQuery getMapreduceCounters(IRlDb rldb, Set<String> stepToken, Set<Long> workflowExecutionIds,
                                                  Set<String> specificGroups,
                                                  Set<String> specificNames) throws IOException {
    return getMapreduceCounters(getStepAttempts(rldb, stepToken, workflowExecutionIds), specificGroups, specificNames);
  }

  public static GenericQuery getMapreduceCounters(GenericQuery stepQuery,
                                                  Set<String> specificGroups,
                                                  Set<String> specificNames) {
    GenericQuery query = stepQuery.innerJoin(MapreduceJob.TBL)
        .on(StepAttempt.ID.equalTo(MapreduceJob.STEP_ATTEMPT_ID.as(Long.class)))
        .innerJoin(MapreduceCounter.TBL)
        .on(MapreduceJob.ID.equalTo(MapreduceCounter.MAPREDUCE_JOB_ID.as(Long.class)));

    if (specificGroups != null) {
      query = query.where(MapreduceCounter.GROUP.in(specificGroups));
    }

    if (specificNames != null) {
      query = query.where(MapreduceCounter.NAME.in(specificNames));
    }

    return query;
  }

  public static GenericQuery getStepAttempts(IRlDb rldb, Set<String> stepTokens, String name, Integer appType, Long startedAfter, Long startedBefore) throws IOException {
    return filterStepAttempts(
        joinStepAttempts(workflowExecutionQuery(rldb, name, appType, startedAfter, startedBefore)),
        stepTokens,
        null
    );
  }

  public static GenericQuery getStepAttempts(IRlDb rldb, Set<String> stepTokens, Set<Long> workflowExecutionIds) throws IOException {

    GenericQuery attempts = rldb.createQuery().from(WorkflowAttempt.TBL)
        .where(WorkflowAttempt.WORKFLOW_EXECUTION_ID.as(Long.class).in(workflowExecutionIds))
        .innerJoin(StepAttempt.TBL)
        .on(WorkflowAttempt.ID.equalTo(StepAttempt.WORKFLOW_ATTEMPT_ID.as(Long.class)));

    return filterStepAttempts(attempts, stepTokens, null);
  }

  private static GenericQuery joinStepAttempts(GenericQuery workflowExecutions) {
    return joinWorkflowAttempts(workflowExecutions)
        .innerJoin(StepAttempt.TBL)
        .on(WorkflowAttempt.ID.equalTo(StepAttempt.WORKFLOW_ATTEMPT_ID.as(Long.class)));
  }

  private static GenericQuery joinWorkflowAttempts(GenericQuery workflowExecutions) {
    return workflowExecutions.innerJoin(WorkflowAttempt.TBL)
        .on(WorkflowExecution.ID.equalTo(WorkflowAttempt.WORKFLOW_EXECUTION_ID.as(Long.class)));
  }

  private static GenericQuery filterStepAttempts(GenericQuery stepQuery, Set<String> stepToken, EnumSet<StepStatus> inStatuses) {

    if (stepToken != null) {
      stepQuery = stepQuery.where(StepAttempt.STEP_TOKEN.in(Sets.newHashSet(stepToken)));
    }

    if (inStatuses != null) {

      Set<Integer> inStatusInts = Sets.newHashSet();
      for (StepStatus status : inStatuses) {
        inStatusInts.add(status.ordinal());
      }

      stepQuery = stepQuery.where(StepAttempt.STEP_STATUS.in(inStatusInts));
    }

    return stepQuery;
  }


}
