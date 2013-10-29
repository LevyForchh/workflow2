package com.rapleaf.cascading_ext.workflow2;

import cascading.flow.Flow;
import cascading.flow.FlowListener;
import cascading.flow.planner.Scope;
import cascading.pipe.Pipe;
import cascading.stats.FlowStepStats;
import cascading.stats.hadoop.HadoopStepStats;
import cascading.tap.Tap;
import cascading.tuple.Fields;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.rapleaf.cascading_ext.HRap;
import com.rapleaf.cascading_ext.datastore.BucketDataStore;
import com.rapleaf.cascading_ext.datastore.DataStore;
import com.rapleaf.cascading_ext.datastore.TupleDataStore;
import com.rapleaf.cascading_ext.datastore.internal.DataStoreBuilder;
import com.rapleaf.cascading_ext.map_side_join.Extractor;
import com.rapleaf.cascading_ext.msj_tap.joiner.TOutputMultiJoiner;
import com.rapleaf.cascading_ext.msj_tap.scheme.MergingScheme;
import com.rapleaf.cascading_ext.msj_tap.tap.MSJTap;
import com.rapleaf.cascading_ext.workflow2.TapFactory.SimpleFactory;
import com.rapleaf.cascading_ext.workflow2.action.CascadingAction;
import com.rapleaf.cascading_ext.workflow2.action.FutureCascadingAction;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CascadingWorkflowBuilder {
  private static final Logger LOG = Logger.getLogger(CascadingWorkflowBuilder.class);

  private DataStoreBuilder dsBuilder;
  private Set<Step> subSteps = Sets.newHashSet();

  private Map<String, TapFactory> pipenameToTap;                     //  tap which provides input
  private Multimap<String, DataStore> pipenameToSourceStore;  //  stores which are the source for the pipe
  private Multimap<String, Step> pipenameToParentStep;        //  what step(s) does the pipe depend on

  private Map<Object, Object> flowProperties;
  private int checkpointCount = 0;

  public CascadingWorkflowBuilder(String workingDir) {
    this(workingDir, Maps.newHashMap());
  }

  public CascadingWorkflowBuilder(String workingDir, Map<Object, Object> flowProperties) {
    this.dsBuilder = new DataStoreBuilder(workingDir + "/temp-stores");
    this.flowProperties = Maps.newHashMap(flowProperties);
    this.pipenameToParentStep = HashMultimap.create();
    this.pipenameToSourceStore = HashMultimap.create();
    this.pipenameToTap = Maps.newHashMap();
  }

  //  bind source and sink taps to the flow.  This will set the creates / readsFrom datastores as well as
  //  create the necessary actions

  public Pipe bindSource(String name, DataStore source) {
    return bindSource(name, Lists.newArrayList(source), new SimpleFactory(source));
  }

  public Pipe bindSource(String name, final Collection<DataStore> inputs) {
    return bindSource(name, inputs, new TapFactory() {
      @Override
      public Tap createTap() {
        return HRap.getMultiTap(inputs);
      }
    });
  }

  public Pipe bindSource(String name, DataStore source, TapFactory tap) {
    return bindSource(name, Lists.newArrayList(source), tap);
  }

  public Pipe bindSource(String name, Collection<DataStore> sources, TapFactory tap) {
    pipenameToSourceStore.putAll(name, sources);
    pipenameToTap.put(name, tap);
    return new Pipe(name);
  }

  public <T extends Comparable> Pipe msj(String name, List<MSJBinding<T>> bindings, final TOutputMultiJoiner<T, ?> joiner) throws IOException {

    //  don't really love the instanceof stuff, but we'll just call it a TODO --ben
    List<SourceMSJBinding<T>> sourceMSJBindings = Lists.newArrayList();
    for (MSJBinding<T> binding : bindings) {

      //  if the MSJ is coming from a cascading flow, bind it to a datastore so the MSJ can read it
      if (binding instanceof FlowMSJBinding) {
        FlowMSJBinding<T> flowBinding = (FlowMSJBinding<T>) binding;

        String stepName = getNextStepName();
        BucketDataStore checkpointStore = dsBuilder.getBucketDataStore(stepName + "-sink", flowBinding.getRecordType());
        Step step = completeFlows(stepName, Lists.newArrayList(new SinkBinding(flowBinding.getPipe(), checkpointStore)), new EmptyListener());

        sourceMSJBindings.add(new SourceMSJBinding<T>(binding.getExtractor(), checkpointStore));

        subSteps.add(step);
        pipenameToParentStep.put(name, step);
      }

      //  otherwise we can read it directly
      else if (binding instanceof SourceMSJBinding) {
        sourceMSJBindings.add((SourceMSJBinding<T>) binding);
      }

      //  no idea
      else {
        throw new RuntimeException("Unrecognized binding class: " + binding.getClass());
      }

    }

    final List<String> sources = Lists.newArrayList();
    final List<Extractor<T>> extractors = Lists.newArrayList();

    for (SourceMSJBinding<T> binding : sourceMSJBindings) {
      BucketDataStore store = binding.getStore();
      sources.add(store.getPath());
      extractors.add(binding.getExtractor());

      pipenameToSourceStore.put(name, store);
    }

    pipenameToTap.put(name, new TapFactory() {
      @Override
      public Tap createTap() throws IOException {
        return MSJTap.of(MergingScheme.of(joiner), sources, extractors);
      }
    });

    return new Pipe(name);
  }

  //  TODO if a pipe has nothing done to it, just attach it to the subsequent MSJ (to allow MSJing a store which has
  //  just been sorted with a previously MSJ compatible datastore in a single flow.  Likewise, if a MSJ is the last step
  //  in a flow, just sink directly and don't create an empty cascading step

  //  checkpoints

  public Pipe addCheckpoint(Pipe endPipe, Fields fields) throws IOException {
    return addCheckpoint(endPipe, getNextStepName(), fields, new EmptyListener());
  }

  public Pipe addCheckpoint(Pipe endPipe, Fields fields, FlowListener flowCompletedCallback) throws IOException {
    return addCheckpoint(endPipe, getNextStepName(), fields, flowCompletedCallback);
  }

  public Pipe addCheckpoint(Pipe endPipe, String checkpointName, Fields fields) throws IOException {
    return addCheckpoint(endPipe, checkpointName, fields, new EmptyListener());
  }

  public Pipe addCheckpoint(Pipe endPipe) throws IOException {
    return addCheckpoint(endPipe, getNextStepName(), determineOutputFields(endPipe), new EmptyListener());
  }

  public Pipe addCheckpoint(Pipe endPipe, FlowListener flowCompletedCallback) throws IOException {
    return addCheckpoint(endPipe, getNextStepName(), determineOutputFields(endPipe), flowCompletedCallback);
  }

  public Pipe addCheckpoint(Pipe endPipe, String checkpointName) throws IOException {
    return addCheckpoint(endPipe, checkpointName, determineOutputFields(endPipe), new EmptyListener());
  }

  public Pipe addCheckpoint(Pipe endPipe, String checkpointName, FlowListener flowListener) throws IOException {
    return addCheckpoint(endPipe, checkpointName, determineOutputFields(endPipe), flowListener);
  }

  public Pipe addCheckpoint(Pipe endPipe, String checkpointName, Fields fields, FlowListener flowListener) throws IOException {
    LOG.info("determined output fields to be " + fields + " for step " + checkpointName);
    TupleDataStore checkpointStore = dsBuilder.getTupleDataStore(checkpointName, fields);

    Step step = completeFlows(checkpointName, Lists.newArrayList(new SinkBinding(endPipe, checkpointStore)), flowListener);

    String nextPipeName = "tail-" + checkpointName;

    subSteps.add(step);
    pipenameToParentStep.put(nextPipeName, step);
    pipenameToSourceStore.put(nextPipeName, checkpointStore);
    pipenameToTap.put(nextPipeName, new SimpleFactory(checkpointStore));

    return new Pipe(nextPipeName);
  }

  //  build the workflow or multi step action

  public Step buildTail(Pipe output, DataStore outputStore) {
    return buildTail(getNextStepName(), output, outputStore);
  }

  public Step buildTail(String tailStepName, Pipe output, DataStore outputStore) {
    return buildTail(tailStepName, Lists.newArrayList(new SinkBinding(output, outputStore)), new EmptyListener());
  }

  public Step buildTail(String tailStepName, Pipe output, DataStore outputStore, FlowListener listener) {
    return buildTail(tailStepName, Lists.newArrayList(new SinkBinding(output, outputStore)), listener);
  }

  public Step buildTail(String tailStepName, List<SinkBinding> sinks) {
    return buildTail(tailStepName, sinks, new EmptyListener());
  }

  public Step buildTail(String tailStepName, List<SinkBinding> sinks, FlowListener listener) {
    Step tail = completeFlows(tailStepName, sinks, listener);

    List<Step> steps = Lists.newArrayList(subSteps);
    steps.add(tail);
    analyze(steps);

    return tail;
  }

  //  internal stuff

  private String getNextStepName() {
    return "step-" + (checkpointCount++);
  }

  private Step completeFlows(String name, List<SinkBinding> sinkBindings, FlowListener flowListener) {
    Map<String, TapFactory> sources = Maps.newHashMap();
    Map<String, TapFactory> sinks = Maps.newHashMap();
    List<DataStore> sinkStores = Lists.newArrayList();

    Set<DataStore> inputs = Sets.newHashSet();
    List<Step> previousSteps = Lists.newArrayList();
    List<Pipe> pipes = Lists.newArrayList();

    for (SinkBinding sinkBinding : sinkBindings) {
      Pipe pipe = sinkBinding.getPipe();
      DataStore dataStore = sinkBinding.getOutputStore();
      Pipe[] heads = pipe.getHeads();

      String pipeName = pipe.getName();
      if (sinks.containsKey(pipeName)) {
        throw new RuntimeException("Pipe with name " + pipeName + " already exists!");
      }

      sinks.put(pipeName, new SimpleFactory(dataStore));

      sources.putAll(createSourceMap(heads));
      inputs.addAll(getInputStores(heads));
      previousSteps.addAll(getPreviousSteps(heads));

      sinkStores.add(dataStore);
      pipes.add(pipe);
    }

    FutureCascadingAction action = new FutureCascadingAction(
        name,
        sources,
        sinks,
        pipes,
        inputs,
        sinkStores,
        flowProperties,
        flowListener
    );

    return new Step(action, previousSteps);
  }

  private static void analyze(List<Step> steps) {

    StringBuilder logMessage = new StringBuilder("\n==================================\n");
    logMessage.append("Your workflow will require ").append(steps.size()).append(" actions\n");
    int numSteps = 0;
    int maps = 0;
    int reduces = 0;

    for (Step subStep : steps) {
      if (subStep.getAction() instanceof CascadingAction) {
        Flow flow = ((CascadingAction) subStep.getAction()).getFlow();
        numSteps += flow.getFlowStats().getStepsCount();
        for (FlowStepStats flowStepStats : flow.getFlowStats().getFlowStepStats()) {
          if (flowStepStats instanceof HadoopStepStats) {
            maps += ((HadoopStepStats) flowStepStats).getNumMapTasks();
            reduces += ((HadoopStepStats) flowStepStats).getNumReduceTasks();
          }
        }
      }
    }

    logMessage.append("Your workflow will require ").append(numSteps).append(" mapreduce jobs\n");
    logMessage.append("Your workflow will require ").append(maps).append(" map tasks\n");
    logMessage.append("Your workflow will require ").append(reduces).append(" reduce tasks\n");

    logMessage.append("==================================");
    LOG.info(logMessage.toString());
  }

  private List<Step> getPreviousSteps(Pipe[] heads) {
    List<Step> output = Lists.newArrayList();
    for (Pipe head : heads) {
      if (pipenameToParentStep.containsKey(head.getName())) {
        output.addAll(pipenameToParentStep.get(head.getName()));
      }
    }
    return output;
  }

  private Map<String, TapFactory> createSourceMap(Pipe[] heads) {
    Map<String, TapFactory> output = Maps.newHashMap();
    for (Pipe head : heads) {
      if (pipenameToTap.containsKey(head.getName())) {
        output.put(head.getName(), pipenameToTap.get(head.getName()));
      } else {
        throw new RuntimeException("Could not find tap for head pipe named " + head.getName());
      }
    }
    return output;
  }

  private Fields determineOutputFields(Pipe tail) throws IOException {
    return getScope(tail).getIncomingTapFields();
  }

  private Scope getScope(Pipe tail) throws IOException {
    Pipe[] previousPipes = tail.getPrevious();
    if (previousPipes.length == 0) {
      Fields sourceFields;
      if (pipenameToTap.containsKey(tail.getName())) {
        sourceFields = pipenameToTap.get(tail.getName()).createTap().getSourceFields();
      } else {
        throw new RuntimeException("Cannot find head pipe name " + tail.getName() + " in any source map during field resolution");
      }
      Scope scope = new Scope(sourceFields);
      scope.setName(tail.getName());
      return scope;
    } else {
      Set<Scope> scopes = Sets.newHashSet();
      for (Pipe previous : previousPipes) {
        Scope scope = getScope(previous);
        scope.setName(previous.getName());
        scopes.add(scope);
      }
      return tail.outgoingScopeFor(scopes);
    }
  }

  private Set<DataStore> getInputStores(Pipe[] heads) {
    Set<DataStore> inputStores = Sets.newHashSet();
    for (Pipe head : heads) {
      if (pipenameToSourceStore.containsKey(head.getName())) {
        inputStores.addAll(pipenameToSourceStore.get(head.getName()));
      } else {
        throw new RuntimeException("Could not find store for head pipe " + head.getName());
      }
    }
    return inputStores;
  }
}

