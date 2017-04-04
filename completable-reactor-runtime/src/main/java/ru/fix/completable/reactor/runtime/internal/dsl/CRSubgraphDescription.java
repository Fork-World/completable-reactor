package ru.fix.completable.reactor.runtime.internal.dsl;

import lombok.Data;
import ru.fix.completable.reactor.api.ReactorGraphModel;
import ru.fix.completable.reactor.runtime.dsl.Subgraph;
import ru.fix.completable.reactor.runtime.dsl.SubgraphDescription;
import ru.fix.completable.reactor.runtime.dsl.SubgraphMerger;

import java.util.function.Function;

/**
 * @author Kamil Asfandiyarov
 */
@Data
public class CRSubgraphDescription<PayloadType> implements SubgraphDescription<PayloadType> {

    final Class<?> subgraphPayload;

    /**
     * Null if there is no merger provided.
     * In that case Processor does not modify payload.
     * {@code BiFunction<Payload, ProcessorResult, Enum>}
     */
    SubgraphMerger<PayloadType, ?> merger;

    /**
     * Source where {@code withMerger} or {@code withoutMerger}  method was used
     */
    ReactorGraphModel.Source mergeSource;

    String subgraphTitle;

    String[] subgraphDoc;

    String[] mergerDocs;

    String mergerTitle;

    /**
     * {@code Function<Payload, Arg>}
     */
    Function<PayloadType, ?> arg;

    boolean isCopyArg = false;

//    /**
//     * {@code Handler1Arg<Processor, Arg1, CompletableFuture<ProcessorResult>>}
//     */
//    Function<?, CompletableFuture<?>> handler;

    public CRSubgraphDescription(Class<?> subgraphPayload) {
        this.subgraphPayload = subgraphPayload;
    }

    public Subgraph<PayloadType> buildSubgraph() {
        return new CRSubgraph<>(this);
    }
}
