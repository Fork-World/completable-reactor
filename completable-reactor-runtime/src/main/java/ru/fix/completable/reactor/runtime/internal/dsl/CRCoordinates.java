package ru.fix.completable.reactor.runtime.internal.dsl;

import lombok.experimental.var;
import lombok.extern.slf4j.Slf4j;
import ru.fix.completable.reactor.api.ReactorGraphModel;
import ru.fix.completable.reactor.runtime.ReactorGraph;
import ru.fix.completable.reactor.runtime.dsl.Coordinates;
import ru.fix.completable.reactor.runtime.internal.CRReactorGraph;
import ru.fix.completable.reactor.runtime.internal.ReactorReflector;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Kamil Asfandiyarov
 */

@Slf4j
public class CRCoordinates<PayloadType> implements Coordinates<PayloadType> {

    final BuilderContext<PayloadType> builderContext;
    final CRReactorGraph<PayloadType> graph;
    final CRGraphBuilder<PayloadType> crGraphBuilder;


    public CRCoordinates(BuilderContext<PayloadType> builderContext) {
        this.builderContext = builderContext;
        this.graph = builderContext.graph;
        this.crGraphBuilder = new CRGraphBuilder<>(builderContext);
    }

    @Override
    public Coordinates<PayloadType> start(int x, int y) {
        graph.getStartPoint().setCoordinates(new ReactorGraphModel.Coordinates(x, y));
        graph.getStartPoint().setCoordinatesSource(ReactorReflector.getMethodInvocationPoint().orElse(null));
        return this;
    }

    @Override
    public Coordinates<PayloadType> proc(Class processorType, int id, int x, int y) {
        var matchedProcessors = graph.getProcessingItems().keySet().stream()
                .filter(processingItem -> CRReactorGraph.serialize(processingItem).equals(CRReactorGraph.serialize(processorType, id)))
                .collect(Collectors.toList());

        if (matchedProcessors.size() > 1) {
            String matchedProcessorIds = matchedProcessors.stream()
                    .map(CRReactorGraph::serialize)
                    .collect(Collectors.joining(","));
            throw new IllegalArgumentException(String.format(
                    "Found multiple processors that matches %s " +
                            "Available processors: %s",
                    CRReactorGraph.serialize(processorType, id),
                    matchedProcessorIds));

        } else if (matchedProcessors.size() <= 0) {
            throw new IllegalArgumentException(String.format("Could not find processor that matches %s. Registered processors: %s",
                    CRReactorGraph.serialize(processorType, id),
                    graph.getProcessingItems().keySet().stream()
                            .map(CRReactorGraph::serialize)
                            .collect(Collectors.joining(","))
            ));
        }

        CRReactorGraph.ProcessingItemInfo processorInfo = graph.getProcessingItems().get(matchedProcessors.get(0));
        processorInfo.setCoordinates(new ReactorGraphModel.Coordinates(x, y));
        processorInfo.setCoordinatesSource(ReactorReflector.getMethodInvocationPoint().orElse(null));

        return this;
    }

    @Override
    public Coordinates<PayloadType> merge(Class processorType, int id, int x, int y) {
        List<CRReactorGraph.MergePoint> matchedMergePoints = graph.getMergePoints().stream()
                .filter(mergePoint -> mergePoint.getType() == CRReactorGraph.MergePoint.Type.PROCESSOR)
                .filter(mergePoint -> CRReactorGraph.serialize(mergePoint.asProcessingItem()).equals(
                        CRReactorGraph.serialize(processorType, id)))
                .collect(Collectors.toList());

        if (matchedMergePoints.size() > 1) {
            String matchedMergePointIds = matchedMergePoints.stream()
                    .map(mergePoint -> CRReactorGraph.serialize(mergePoint.asProcessingItem()))
                    .collect(Collectors.joining(","));
            throw new IllegalArgumentException(String.format(
                    "Found multiple merge points that matches %s." +
                            " Merge points: %s",
                    CRReactorGraph.serialize(processorType, id),
                    matchedMergePointIds));

        } else if (matchedMergePoints.size() <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Could not find merge point that matches %s",
                    CRReactorGraph.serialize(processorType, id)));
        }

        CRReactorGraph.MergePoint mergePoint = matchedMergePoints.get(0);
        mergePoint.setCoordinates(new ReactorGraphModel.Coordinates(x, y));
        return this;
    }

    @Override
    public Coordinates<PayloadType> merge(int id, int x, int y) {
        List<CRReactorGraph.MergePoint> matchedMergePoints = graph.getMergePoints().stream()
                .filter(mergePoint -> mergePoint.getType() == CRReactorGraph.MergePoint.Type.DETACHED)
                .filter(mergePoint -> CRReactorGraph.serialize(mergePoint.asProcessingItem()).equals(
                        CRReactorGraph.serializeMergePoint(id)))
                .collect(Collectors.toList());

        if (matchedMergePoints.size() > 1) {
            String matchedMergePointIds = matchedMergePoints.stream()
                    .map(mergePoint -> CRReactorGraph.serialize(mergePoint.asProcessingItem()))
                    .collect(Collectors.joining(","));
            throw new IllegalArgumentException(String.format(
                    "Found multiple merge points that matches %s." +
                            " Merge points: %s",
                    CRReactorGraph.serializeMergePoint(id),
                    matchedMergePointIds));

        } else if (matchedMergePoints.size() <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Could not find merge point that matches %s",
                    CRReactorGraph.serializeMergePoint(id)));
        }

        CRReactorGraph.MergePoint mergePoint = matchedMergePoints.get(0);
        mergePoint.setCoordinates(new ReactorGraphModel.Coordinates(x, y));
        return this;
    }

    @Override
    public Coordinates<PayloadType> complete(Class processorOrSubgraphType, int id, int x, int y) {
        List<CRReactorGraph.MergePoint> matchedMergePoints = graph.getMergePoints().stream()
                .filter(mergePoint -> mergePoint.getType() == CRReactorGraph.MergePoint.Type.PROCESSOR)
                .filter(mergePoint -> CRReactorGraph.serialize(mergePoint.getProcessor()).equals(
                        CRReactorGraph.serialize(processorOrSubgraphType, id)))
                .collect(Collectors.toList());

        if (matchedMergePoints.size() > 1) {
            String matchedMergePointIds = matchedMergePoints.stream()
                    .map(mergePoint -> CRReactorGraph.serialize(mergePoint.getProcessor()))
                    .collect(Collectors.joining(","));

            throw new IllegalArgumentException(String.format(
                    "Found multiple merge points that matches %s. Available merge points: %s",
                    CRReactorGraph.serialize(processorOrSubgraphType, id),
                    matchedMergePointIds));

        } else if (matchedMergePoints.size() <= 0) {
            throw new IllegalArgumentException(String.format("Could not find merge point that matches %s",
                    CRReactorGraph.serialize(processorOrSubgraphType, id)));
        }

        List<CRReactorGraph.Transition> transitions = matchedMergePoints.get(0).getTransitions().stream()
                .filter(CRReactorGraph.Transition::isComplete)
                .collect(Collectors.toList());

        if (transitions.size() > 1) {
            throw new IllegalArgumentException(String.format("Found multiple complete transitions that matches %s",
                    CRReactorGraph.serialize(processorOrSubgraphType, id)));
        }
        else if (transitions.size() <= 0) {
            throw new IllegalArgumentException(String.format("Could not find complete transition that matches %s",
                    CRReactorGraph.serialize(processorOrSubgraphType, id)));
        }

        CRReactorGraph.Transition transition = transitions.get(0);
        transition.setCompleteCoordinates(new ReactorGraphModel.Coordinates(x, y));
        transition.setCompleteCoordinatesSource(ReactorReflector.getMethodInvocationPoint().orElse(null));

        return this;
    }

    @Override
    public Coordinates<PayloadType> complete(int id, int x, int y) {
        List<CRReactorGraph.MergePoint> matchedMergePoints = graph.getMergePoints().stream()
                .filter(mergePoint -> mergePoint.getType() == CRReactorGraph.MergePoint.Type.DETACHED)
                .filter(mergePoint -> CRReactorGraph.serialize(mergePoint.getProcessor()).equals(
                        CRReactorGraph.serializeMergePoint(id)))
                .collect(Collectors.toList());

        if (matchedMergePoints.size() > 1) {
            String matchedMergePointIds = matchedMergePoints.stream()
                    .map(mergePoint -> CRReactorGraph.serialize(mergePoint.getProcessor()))
                    .collect(Collectors.joining(","));

            throw new IllegalArgumentException(String.format(
                    "Found multiple merge points that matches %s. Available merge points: %s",
                    CRReactorGraph.serializeMergePoint(id),
                    matchedMergePointIds));

        } else if (matchedMergePoints.size() <= 0) {
            throw new IllegalArgumentException(String.format("Could not find merge point that matches %s",
                    CRReactorGraph.serializeMergePoint(id)));
        }

        List<CRReactorGraph.Transition> transitions = matchedMergePoints.get(0).getTransitions().stream()
                .filter(CRReactorGraph.Transition::isComplete)
                .collect(Collectors.toList());

        if (transitions.size() > 1) {
            throw new IllegalArgumentException(String.format("Found multiple complete transitions that matches %s",
                    CRReactorGraph.serializeMergePoint(id)));
        }
        else if (transitions.size() <= 0) {
            throw new IllegalArgumentException(String.format("Could not find complete transition that matches %s",
                    CRReactorGraph.serializeMergePoint(id)));
        }

        CRReactorGraph.Transition transition = transitions.get(0);
        transition.setCompleteCoordinates(new ReactorGraphModel.Coordinates(x, y));
        transition.setCompleteCoordinatesSource(ReactorReflector.getMethodInvocationPoint().orElse(null));

        return this;
    }

    @Override
    public ReactorGraph<PayloadType> buildGraph() {
        return crGraphBuilder.buildGraph();
    }
}
