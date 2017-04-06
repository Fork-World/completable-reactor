package ru.fix.completable.reactor.runtime.internal.dsl;

import lombok.val;
import ru.fix.completable.reactor.runtime.dsl.*;
import ru.fix.completable.reactor.runtime.internal.CRProcessingItem;
import ru.fix.completable.reactor.runtime.internal.CRReactorGraph;

import java.util.stream.Collectors;

/**
 * @author Kamil Asfandiyarov
 */
public class CRMergeGroupBuilder<PayloadType> implements MergeGroupBuilder<PayloadType> {
    final BuilderContext<PayloadType> builderContext;
    final CRReactorGraph<PayloadType> graph;
    final CRReactorGraph.MergeGroup graphMergeGroup;

    public CRMergeGroupBuilder(BuilderContext<PayloadType> builderContext) {
        this.builderContext = builderContext;
        this.graph = builderContext.graph;
        this.graphMergeGroup = new CRReactorGraph.MergeGroup();
        graph.getMergeGroups().add(graphMergeGroup);
    }


    void addMergePointToMergeGroup(CRProcessingItem processingItem) {

        val existingMergePoints = graph.getMergePoints().stream()
                .filter(mergePoint -> mergePoint.asProcessingItem().equals(processingItem))
                .collect(Collectors.toList());

        if(existingMergePoints.size() != 1){
            throw new IllegalArgumentException(String.format(
                    "There should be only one merge point %s registered in the graph. Actual: %d",
                    processingItem.getDebugName(),
                    existingMergePoints.size()));
        }

        graphMergeGroup.getMergePoints().add(existingMergePoints.get(0));
    }



    @Override
    public MergeGroupBuilder<PayloadType> with(Processor<? super PayloadType> processor) {
        CRProcessor<?> crProcessor = (CRProcessor<?>) processor;
        graph.ensureProcessingItemRegistered(crProcessor);

        addMergePointToMergeGroup(crProcessor);
        return this;
    }

    @Override
    public MergeGroupBuilder<PayloadType> with(Subgraph<? super PayloadType> subgraph) {
        val crSubgraph = (CRSubgraph<?>) subgraph;
        graph.ensureProcessingItemRegistered(crSubgraph);

        addMergePointToMergeGroup(crSubgraph);
        return this;
    }

    @Override
    public MergeGroupBuilder<PayloadType> with(MergePoint<PayloadType> mergePoint) {
        val crMergePoint = (CRMergePoint) mergePoint;
        graph.ensureProcessingItemRegistered(crMergePoint);

        addMergePointToMergeGroup(crMergePoint);
        return this;
    }

    private void assertMergeGroup(){
        if(graphMergeGroup.getMergePoints().size() < 2){
            throw new IllegalArgumentException(String.format(
                    "Merge group should contain at least two items: %s",
                    graphMergeGroup.getMergePoints().stream()
                            .map(CRReactorGraph.MergePoint::asProcessingItem)
                            .map(CRProcessingItem::getDebugName)
                            .collect(Collectors.joining(","))));
        }
    }

    @Override
    public MergePointBuilder<PayloadType> mergePoint(Processor<? super PayloadType> processor) {
        assertMergeGroup();
        return CRMergePointBuilder.startBuildingMergePoint(builderContext, processor);
    }

    @Override
    public MergePointBuilder<PayloadType> mergePoint(MergePoint<PayloadType> mergePoint) {
        assertMergeGroup();
        return CRMergePointBuilder.startBuildingMergePoint(builderContext, mergePoint);
    }
}
