package com.rapleaf.cascading_ext.workflow2;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.scribe.utils.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cascading.flow.Flow;
import cascading.flow.FlowConnector;
import cascading.pipe.Pipe;
import cascading.tap.Tap;

import com.liveramp.cascading_ext.CascadingUtil;
import com.liveramp.cascading_ext.FileSystemHelper;
import com.liveramp.cascading_ext.flow.JobPersister;
import com.liveramp.cascading_ext.fs.TrashHelper;
import com.liveramp.cascading_ext.megadesk.StoreReaderLocker;
import com.liveramp.cascading_ext.resource.ReadResource;
import com.liveramp.cascading_ext.resource.Resource;
import com.liveramp.cascading_tools.jobs.TrackedFlow;
import com.liveramp.cascading_tools.jobs.TrackedOperation;
import com.liveramp.commons.collections.properties.NestedProperties;
import com.liveramp.commons.collections.properties.OverridableProperties;
import com.liveramp.team_metadata.paths.hdfs.TeamTmpDir;
import com.liveramp.workflow_core.OldResource;
import com.liveramp.workflow_core.runner.BaseAction;
import com.liveramp.workflow_state.DSAction;
import com.liveramp.workflow_state.DataStoreInfo;
import com.rapleaf.cascading_ext.CascadingHelper;
import com.rapleaf.cascading_ext.datastore.DataStore;
import com.rapleaf.cascading_ext.datastore.internal.DataStoreBuilder;
import com.rapleaf.cascading_ext.workflow2.counter.CounterFilter;
import com.rapleaf.cascading_ext.workflow2.counter.verifier.TemplateTapFiles;
import com.rapleaf.cascading_ext.workflow2.flow_closure.FlowRunner;

public abstract class Action extends BaseAction<WorkflowRunner.ExecuteConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(Action.class);

  private final HdfsActionContext context;

  private StoreReaderLocker.LockManager lockManager;

  private final Multimap<DSAction, DataStore> datastores = HashMultimap.create();


  public Action(String checkpointToken) {
    this(checkpointToken, Maps.newHashMap());
  }

  public Action(String checkpointToken, String tmpRoot) {
    this(checkpointToken, tmpRoot, Maps.newHashMap());
  }

  public Action(String checkpointToken, Map<Object, Object> properties) {
    this(checkpointToken, null, properties);
  }

  public Action(String checkpointToken, String tmpRoot, Map<Object, Object> properties) {
    super(checkpointToken, properties);
    this.context = new HdfsActionContext(tmpRoot, checkpointToken);
  }

  protected FileSystem getFS() throws IOException {
    return context.getFS();
  }

  public final String getTmpRoot() {
    return context.getTmpRoot();
  }

  //  datastore actions

  protected void readsFrom(DataStore store) {
    mark(DSAction.READS_FROM, store);
  }

  protected void creates(DataStore store) {
    mark(DSAction.CREATES, store);
  }

  protected void createsTemporary(DataStore store) {
    mark(DSAction.CREATES_TEMPORARY, store);
  }

  protected void writesTo(DataStore store) {
    mark(DSAction.WRITES_TO, store);
  }

  protected void consumes(DataStore store) {
    mark(DSAction.CONSUMES, store);
  }

  private void mark(DSAction action, DataStore store) {
    Preconditions.checkNotNull(store, "Cannot mark a null datastore as used!");
    datastores.put(action, store);
  }

  public DataStoreBuilder builder() {
    return context.getBuilder();
  }

  @Override
  protected final void preExecute() throws Exception {
    //  only set properties not explicitly set by the step

    prepDirs();

    lockManager.lockConsumeStart();

  }

  @Override
  protected final void postExecute() {
    lockManager.release();
  }

  private Set<DataStore> getDatastores(DSAction... actions) {
    Set<DataStore> stores = Sets.newHashSet();

    for (DSAction dsAction : actions) {
      stores.addAll(datastores.get(dsAction));
    }

    return stores;
  }

  @Override
  public Multimap<DSAction, DataStoreInfo> getAllDataStoreInfo() {

    Multimap<DSAction, DataStoreInfo> stores = HashMultimap.create();

    for (Map.Entry<DSAction, DataStore> entry : datastores.entries()) {
      stores.put(entry.getKey(), new DataStoreInfo(
          entry.getValue().getName(),
          entry.getClass().getName(),
          entry.getValue().getPath()
      ));
    }
    return stores;
  }


  @SuppressWarnings("PMD.BlacklistedMethods") //  temporary hopefully, until we get more cluster space
  private void prepDirs() throws Exception {
    FileSystem fs = FileSystemHelper.getFS();
    for (DataStore ds : getDatastores(DSAction.CREATES, DSAction.CREATES_TEMPORARY)) {
      String uri = new URI(ds.getPath()).getPath();
      Path path = new Path(ds.getPath());
      Boolean trashEnabled = TrashHelper.isEnabled();

      if (fs.exists(path)) {
        // delete if tmp store, or if no trash is enabled
        if (TeamTmpDir.pathIsInTmpDir(uri) || !trashEnabled) {
          LOG.info("Deleting " + uri);
          fs.delete(path, true);
          // otherwise, move to trash
        } else {
          LOG.info("Moving to trash: " + uri);
          TrashHelper.moveToTrash(fs, path);
        }
      }
    }
  }


  @Override
  protected void initialize(WorkflowRunner.ExecuteConfig context) {
    this.lockManager = context.getLockProvider()
        .createManager(getDatastores(DSAction.READS_FROM))
        .lockProcessStart();
  }

  protected StoreReaderLocker getLockProvider() {
    return getConfig().getLockProvider();
  }

  protected FlowBuilder buildFlow(Map<Object, Object> properties) {
    return new FlowBuilder(buildFlowConnector(getInheritedProperties(properties)), getClass());
  }

  protected Map<Object, Object> getInheritedProperties() {
    return getInheritedProperties(Maps.newHashMap());
  }

  protected Map<Object, Object> getInheritedProperties(Map<Object, Object> childProperties) {

    NestedProperties childProps = new NestedProperties(childProperties, false);

    OverridableProperties combinedProperties = getCombinedProperties();

    if (combinedProperties != null) {
      return childProps.override(combinedProperties).getPropertiesMap();
    }
    //TODO Sweep direct calls to execute() so we don't have to do this!
    else {
      return childProps.override(getStepProperties().override(CascadingHelper.get().getDefaultHadoopProperties()))
          .getPropertiesMap();
    }
  }

  protected FlowBuilder buildFlow() {
    return buildFlow(Maps.newHashMap());
  }

  protected FlowConnector buildFlowConnector() {
    return CascadingHelper.get().getFlowConnector(getCombinedProperties().getPropertiesMap());
  }

  private FlowConnector buildFlowConnector(Map<Object, Object> properties) {
    return CascadingUtil.buildFlowConnector(
        new JobConf(),
        getPersister(),
        properties,
        CascadingHelper.get().resolveFlowStepStrategies(),
        CascadingHelper.get().getInvalidPropertyValues()
    );
  }

  protected Flow completeWithProgress(FlowBuilder.IFlowClosure flowc) {
    return completeWithProgress(flowc, false);
  }

  //  TODO sweep when we figure out cascading npe (prolly upgrade past 2.5.1)
  protected Flow completeWithProgress(FlowBuilder.IFlowClosure flowc, boolean skipCompleteListener) {
    Flow flow = flowc.buildFlow();

    TrackedOperation tracked = new TrackedFlow(flow, skipCompleteListener);
    completeWithProgress(tracked);

    return flow;
  }


  protected JobPersister getPersister() {
    return new WorkflowJobPersister(
        getPersistence(),
        getActionId().resolve(),
        getCounterFilter(),
        Lists.<WorkflowJobPersister.CounterVerifier>newArrayList(new TemplateTapFiles())
    );
  }

  //  TODO sweep after killing thing that run steps stupidly
  private CounterFilter getCounterFilter() {
    WorkflowRunner.ExecuteConfig config = getConfig();
    if (config != null) {
      return config.getCounterFilter();
    }
    return null;
  }

  protected void completeWithProgress(TrackedOperation tracked) {
    JobPersister persister = getPersister();
    tracked.complete(
        persister,
        isFailOnCounterFetch()
    );
  }

  protected FlowRunner completeWithProgressClosure() {
    return new ActionFlowRunner();
  }

  private class ActionFlowRunner implements FlowRunner {
    @Override
    public Flow complete(Properties properties, String name, Tap source, Tap sink, Pipe tail) {
      return completeWithProgress(buildFlow(properties).connect(name, source, sink, tail));
    }
  }

  //  everything we feel like exposing to pre-execute hooks in CA2.  I don't really love that it's here, but this way
  //  we don't have to make these methods public.  there should be a cleaner way but I can't think of it.
  public class PreExecuteContext {

    public <T> T get(OldResource<T> resource) throws IOException {
      return Action.this.get(resource);
    }

    public <T> T get(ReadResource<T> resource) {
      return Action.this.get(resource);
    }

  }

  //  stuff available for during action construction
  public class ConstructContext {

    public void creates(DataStore store) {
      Action.this.creates(store);
    }

    public <T> void uses(OldResource<T> resource) {
      Action.this.uses(resource);
    }

    public <T> ReadResource<T> readsFrom(Resource<T> resource) {
      return Action.this.readsFrom(resource);
    }

  }

  public PreExecuteContext getPreExecuteContext() {
    return new PreExecuteContext();
  }

  public ConstructContext getConstructContext() {
    return new ConstructContext();
  }

}
