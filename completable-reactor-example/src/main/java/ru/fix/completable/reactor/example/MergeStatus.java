package ru.fix.completable.reactor.example;

import ru.fix.completable.reactor.api.TransitionDescription;

/**
 * @author Kamil Asfandiyarov
 */
public enum MergeStatus {
    @TransitionDescription(doc = "Continue processing")
    CONTINUE,

    @TransitionDescription(doc = "Stop processing")
    STOP,

    @TransitionDescription(doc = "Withdraw money required")
    WITHDRAWAL,

    @TransitionDescription(doc = "No withdrawal required")
    NO_WITHDRAWAL
}
