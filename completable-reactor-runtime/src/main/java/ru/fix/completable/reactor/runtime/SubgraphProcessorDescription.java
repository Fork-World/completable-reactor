package ru.fix.completable.reactor.runtime;

import ru.fix.completable.reactor.api.ReactorGraphModel;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Describe subgraph handing and merging
 * @author Kamil Asfandiyarov
 */
public class SubgraphProcessorDescription<SubgraphPayloadType, PayloadType> {

    final Class<SubgraphPayloadType> subgraphPayload;

    /**
     * Null if there is no merger provided.
     * In that case Processor does not modify payload.
     * {@code BiFunction<Payload, ProcessorResult, Enum>}
     */
    BiFunction<PayloadType, SubgraphPayloadType, Enum> merger;

    /**
     * Source where {@code withMerger} or {@code withoutMerger}  method was used
     */
    ReactorGraphModel.Source mergeSource;

    String[] mergerDocs;

    /**
     * {@code Function<Payload, Arg>}
     */
    Function<PayloadType, SubgraphPayloadType> arg;

    boolean isCopyArg = false;

    /**
     * {@code MethodReference1Arg<Processor, Arg1, CompletableFuture<ProcessorResult>>}
     */
    Function<SubgraphPayloadType, CompletableFuture<SubgraphPayloadType>> handler;

    SubgraphProcessorDescription(Class<SubgraphPayloadType> subgraphPayload) {
        this.subgraphPayload = subgraphPayload;
    }

    public SubgraphProcessor<SubgraphPayloadType, PayloadType> buildSubgraph() {
        return new SubgraphProcessor<>(this);
    }
}
