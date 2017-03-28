package ru.fix.completable.reactor.runtime.function;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;

/**
 * @author Kamil Asfandiyarov
 */
@FunctionalInterface
public interface Handler4Args<Arg1, Arg2, Arg3, Arg4, Result>  extends Serializable{
    CompletableFuture<Result> apply(Arg1 arg1, Arg2 arg2, Arg3 arg3, Arg4 arg4);
}
