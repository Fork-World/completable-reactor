package ru.fix.completable.reactor.runtime.dsl;

import java.util.function.Function;

/**
 * @author Kamil Asfandiyarov
 */
public interface HandlerBuilder0<PayloadType> {

    <ProcessorResult> ProcessorMergerBuilder<PayloadType, ProcessorResult> withHandler(
            Handler0Args<ProcessorResult> handler
    );

    <Arg1> HandlerBuilder1<PayloadType, Arg1> passArg(
            Function<PayloadType, Arg1> arg
    );

    <Arg1> HandlerBuilder1<PayloadType, Arg1> copyArg(
            Function<PayloadType, Arg1> arg
    );
}
