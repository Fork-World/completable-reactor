package ru.fix.completable.reactor.runtime;

import ru.fix.completable.reactor.runtime.internal.ProcessingGraphItem;

/**
 * @author Kamil Asfandiyarov
 */
public class GraphMergePoint<PayloadType> implements ProcessingGraphItem, MergeableProcessingGraphItem<PayloadType> {
    int id = 0;

    GraphMergePointDescription<PayloadType> mergePointDescription;

    GraphMergePoint(GraphMergePointDescription<PayloadType> mergePointDescription) {
        this.mergePointDescription = mergePointDescription;
    }

    public GraphMergePoint<PayloadType> withId(int id){
        this.id = id;
        return this;
    }

    @Override
    public String getProfilingName() {
        return "mergePoint." + this.id;
    }

    @Override
    public String getDebugName() {
        return "mergePoint@" + id;
    }
}
