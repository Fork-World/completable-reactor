package ru.fix.completable.reactor.runtime.tests;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import ru.fix.commons.profiler.impl.SimpleProfiler;
import ru.fix.completable.reactor.api.Reactored;
import ru.fix.completable.reactor.runtime.CompletableReactor;
import ru.fix.completable.reactor.runtime.ReactorGraph;
import ru.fix.completable.reactor.runtime.ReactorGraphBuilder;
import ru.fix.completable.reactor.runtime.dsl.MergePoint;
import ru.fix.completable.reactor.runtime.dsl.Processor;
import ru.fix.completable.reactor.runtime.dsl.Subgraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * @author Kamil Asfandiyarov
 */
@Slf4j
public class CompletableReactorTest {

    private SimpleProfiler profiler;
    private CompletableReactor reactor;
    private ReactorGraphBuilder graphBuilder;


    enum Status {OK, UNUSED}


    @Reactored({
            "IdListPayload contains list of integer ids.",
            " When IdListPayload goes through processors chain each processor adds their id",
            " so at the end we can clarify by witch processor and in what order this payload was processed."})
    @Data
    @Accessors(chain = true)
    static class IdListPayload {
        final List<Integer> idSequence = new ArrayList<>();

    }

    @Before
    public void before() throws Exception {
        profiler = new SimpleProfiler();
        reactor = new CompletableReactor(profiler)
                .setDebugProcessingVertexGraphState(true);

        graphBuilder = new ReactorGraphBuilder();


    }

    Processor<IdListPayload> buildProcessor(IdProcessor idProcessor){
        return graphBuilder.processor()
                .forPayload(IdListPayload.class)
                .withHandler(idProcessor::handle)
                .withMerger((pld, id) -> {
                    pld.getIdSequence().add(id);
                    return Status.OK;
                })
                .buildProcessor();
    }

    static void printGraph(ReactorGraph<?>... graphs) throws Exception {
        CompletableReactor.write(graphs);
    }


    @Reactored({
            "Test will check that single processor id end up at payloads idList.",
            "Expected result: {1}"
    })
    static class SingleProcessorPayload extends IdListPayload {}

    @Test
    public void single_processor() throws Exception {

        Processor<IdListPayload> idProcessor1 = buildProcessor(new IdProcessor(1));

        ReactorGraph graph = graphBuilder.payload(SingleProcessorPayload.class)
                .handleBy(idProcessor1)

                .mergePoint(idProcessor1)
                .onAny().complete()

                .coordinates()
                .start(226, 98)
                .proc(IdProcessor.class, 0, 261, 163)
                .merge(IdProcessor.class, 0, 300, 251)
                .complete(IdProcessor.class, 0, 308, 336)

                .buildGraph();

        printGraph(graph);

        reactor.registerReactorGraph(graph);

        SingleProcessorPayload resultPayload = reactor.submit(new SingleProcessorPayload())
                .getResultFuture()
                .get(10, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(1), resultPayload.getIdSequence());
    }

    @Reactored({
            "Test will check that two processor ids end up at payloads idList in correct order.",
            "Expected result: {1, 2}"
    })
    static class TwoProcessorSequentialMergePayload extends IdListPayload {}

    @Test
    public void two_processors_sequential_merge() throws Exception {

        Processor<IdListPayload> idProcessor1 = buildProcessor(new IdProcessor(1)).setId(1);
        Processor<IdListPayload> idProcessor2 = buildProcessor(new IdProcessor(2)).setId(2);

        ReactorGraph graph = graphBuilder.payload(TwoProcessorSequentialMergePayload.class)

                
                .handleBy(idProcessor1)
                .handleBy(idProcessor2)

                .mergePoint(idProcessor1)
                .on(Status.OK, Status.UNUSED).merge(idProcessor2)

                .mergePoint(idProcessor2)
                .onAny().complete()

                .coordinates()
                .start(366, 103)
                .proc(IdProcessor.class, 1, 358, 184)
                .proc(IdProcessor.class, 2, 549, 183)
                .merge(IdProcessor.class, 1, 427, 291)
                .merge(IdProcessor.class, 2, 571, 356)
                .complete(IdProcessor.class, 2, 610, 454)

                .buildGraph();

        printGraph(graph);

        reactor.registerReactorGraph(graph);


        TwoProcessorSequentialMergePayload resultPayload = reactor.submit(new TwoProcessorSequentialMergePayload())
                .getResultFuture()
                .get(10, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(1, 2), resultPayload.getIdSequence());
    }

    @Reactored({
            "Detached processor is a processor without merger.",
            "Main flow dow not wait for detached processor to complete.",
            "In test Detached processor will run and complete deferred in time.",
            "When result of chain is ready detached processor will be activated.",
            "Test will check that chain execution will complete on detached processor finish.",
            "Expected result: {1, 2}"
    })
    static class DetachedProcessorPayload extends IdListPayload {}

    @Test
    public void detached_processor() throws Exception {

        Processor<IdListPayload> idProcessor1 = buildProcessor(new IdProcessor(1)).setId(1);
        Processor<IdListPayload> idProcessor2 = buildProcessor(new IdProcessor(2)).setId(2);
        IdProcessor detachedProcessor = new IdProcessor(3).withLaunchingLatch();

        Processor<DetachedProcessorPayload> idProcessor3 = graphBuilder.processor()
                .forPayload(DetachedProcessorPayload.class)
                .withHandler(detachedProcessor::handle)
                .withoutMerger()
                .buildProcessor()
                .setId(3);

        ReactorGraph graph = graphBuilder.payload(DetachedProcessorPayload.class)
                
                .handleBy(idProcessor1)
                .handleBy(idProcessor2)
                .handleBy(idProcessor3)

                .mergePoint(idProcessor1)
                .on(Status.OK).merge(idProcessor2)

                .mergePoint(idProcessor2)
                .onAny().complete()

                .coordinates()
                .start(489, 96)
                .proc(IdProcessor.class, 1, 364, 178)
                .proc(IdProcessor.class, 2, 530, 180)
                .proc(IdProcessor.class, 3, 701, 172)
                .merge(IdProcessor.class, 1, 414, 268)
                .merge(IdProcessor.class, 2, 589, 341)
                .complete(IdProcessor.class, 2, 701, 378)

                .buildGraph();

        printGraph(graph);

        reactor.registerReactorGraph(graph);

        CompletableReactor.Execution<DetachedProcessorPayload> result = reactor.submit(new DetachedProcessorPayload());

        assertEquals(Arrays.asList(1, 2), result.getResultFuture().get(5, TimeUnit.SECONDS).getIdSequence());

        assertTrue("result future is complete", result.getResultFuture().isDone());
        assertFalse("execution chain is not complete since detached processor still working", result.getChainExecutionFuture().isDone());

        detachedProcessor.launch();

        result.getChainExecutionFuture().get(5, TimeUnit.SECONDS);
        assertTrue("execution chain is complete when detached processor is finished", result.getChainExecutionFuture().isDone());
    }

    @Reactored({
            "Merge group joins merge points in a way that all outgoing transitions",
            "will be activated only after all merge points from group complete.",
            "Test will check that merge points outgoing transitions activated only after all incoming",
            " into merge group transitions completes."
    })
    static class PayloadWithMergeGroup extends IdListPayload {}

    @Test
    public void outgoing_transitions_should_wait_all_merges_to_complete_in_merge_grup() throws Exception {

        AtomicBoolean mergingIsDone = new AtomicBoolean();

        Processor<PayloadWithMergeGroup> idProcessor1 = graphBuilder.processor()
                .forPayload(PayloadWithMergeGroup.class)
                .withHandler(new IdProcessor(1)::handle)
                .withMerger((pld, id) -> {
                    mergingIsDone.set(true);

                    pld.getIdSequence().add(id);
                    return Status.OK;
                })
                .buildProcessor()
                .setId(1);

        Processor<IdListPayload> idProcessor2 = buildProcessor(new IdProcessor(2)).setId(2);

        IdProcessor deferredProcessor3 = new IdProcessor(3).withLaunchingLatch();
        Processor<IdListPayload> idProcessor3 = buildProcessor(deferredProcessor3).setId(3);


        ReactorGraph graph = graphBuilder.payload(PayloadWithMergeGroup.class)
                
                .handleBy(idProcessor1)
                .handleBy(idProcessor2)
                .handleBy(idProcessor3)

                .mergePoint(idProcessor1)
                .onAny().merge(idProcessor2)

                .mergePoint(idProcessor2)
                .onAny().merge(idProcessor3)

                .mergePoint(idProcessor3)
                .onAny().complete()

                .mergeGroup()
                .with(idProcessor1)
                .with(idProcessor2)
                .with(idProcessor3)

                .coordinates()
                .start(500, 100)
                .proc(IdProcessor.class, 1, 100, 100)
                .proc(IdProcessor.class, 2, 100, 100)
                .proc(IdProcessor.class, 3, 638, 179)
                .merge(IdProcessor.class, 1, 100, 100)
                .merge(IdProcessor.class, 2, 100, 100)
                .merge(IdProcessor.class, 3, 100, 100)
                .complete(IdProcessor.class, 3, 100, 100)

                .buildGraph();

        printGraph(graph);

        reactor.registerReactorGraph(graph);

        CompletableReactor.Execution<PayloadWithMergeGroup> result = reactor.submit(new PayloadWithMergeGroup(), TimeUnit.DAYS.toMillis(1));
        try {
            log.info("Waiting for 1 seconds to ensure that merge group waits all merge points to complete.");
            result.getResultFuture().get(1, TimeUnit.SECONDS);

            fail("result future completed before processor 3 is complete");
        } catch (TimeoutException exc) {
        }
        log.info("Done waiting.");

        assertFalse("merging within merge group starts only after all processors handlings is complete", mergingIsDone.get());

        deferredProcessor3.launch();

        assertEquals(Arrays.asList(1, 2, 3), result.getResultFuture().get(3, TimeUnit.SECONDS).getIdSequence());

        assertTrue("merging within merge group starts only after all processors handlings is complete", mergingIsDone.get());
        assertTrue("result future is complete", result.getResultFuture().isDone());

        result.getChainExecutionFuture().get(5, TimeUnit.SECONDS);
        assertTrue("execution chain is complete", result.getChainExecutionFuture().isDone());
    }

    @Reactored({
            "Subgraph behave the same way as plain processor.",
            "The only difference is that instead of simple async operation CompletableReactor launches subgraph execution"
    })
    static class SubgraphPayload extends IdListPayload {
    }

    @Reactored({
            "Parent graph is a simple graph that calls subgraph during its flow."
    })
    static class ParentGraphPayload extends IdListPayload {}

    @Test
    public void run_subgraph() throws Exception {
        Processor<IdListPayload> idProcessor11 = buildProcessor(new IdProcessor(11)).setId(11);
        Processor<IdListPayload> idProcessor12 = buildProcessor(new IdProcessor(12)).setId(12);
        Processor<IdListPayload> idProcessor13 = buildProcessor(new IdProcessor(13)).setId(13);


        ReactorGraph<SubgraphPayload> childGraph = graphBuilder.payload(SubgraphPayload.class)
                .handleBy(idProcessor11)
                .handleBy(idProcessor12)

                .mergePoint(idProcessor11).onAny().merge(idProcessor12)
                .mergePoint(idProcessor12).onAny().handleBy(idProcessor13)

                .mergeGroup()
                .with(idProcessor11)
                .with(idProcessor12)

                .mergePoint(idProcessor13).onAny().complete()
                .coordinates()
                .proc(IdProcessor.class, 11, 306, 216)
                .proc(IdProcessor.class, 12, 612, 218)
                .proc(IdProcessor.class, 13, 539, 596)
                .merge(IdProcessor.class, 11, 430, 365)
                .merge(IdProcessor.class, 12, 620, 421)
                .merge(IdProcessor.class, 13, 613, 693)
                .start(500, 100)
                .complete(IdProcessor.class, 13, 587, 776)

                .buildGraph();

        reactor.registerReactorGraph(childGraph);

        Processor<IdListPayload> idProcessor1 = buildProcessor(new IdProcessor(1)).setId(1);
        Processor<IdListPayload> idProcessor2 = buildProcessor(new IdProcessor(2)).setId(2);
        Processor<IdListPayload> idProcessor3 = buildProcessor(new IdProcessor(3)).setId(3);

        Subgraph<ParentGraphPayload> subgraphProcessor = graphBuilder.subgraph(SubgraphPayload.class)
                .forPayload(ParentGraphPayload.class)
                .passArg(payload -> new SubgraphPayload())
                .withMerger((payload, result) -> {
                    payload.getIdSequence().addAll(result.getIdSequence());
                    return Status.OK;
                })
                .buildSubgraph();

        ReactorGraph<ParentGraphPayload> parentGraph = graphBuilder.payload(ParentGraphPayload.class)
                
                .handleBy(idProcessor1)

                .mergePoint(idProcessor1)
                .onAny().handleBy(idProcessor2)
                .onAny().handleBy(subgraphProcessor)

                .mergePoint(subgraphProcessor).onAny().merge(idProcessor2)
                .mergePoint(idProcessor2).onAny().handleBy(idProcessor3)

                .mergeGroup().with(subgraphProcessor).with(idProcessor2)

                .mergePoint(idProcessor3).onAny().complete()
                .coordinates()

                .proc(IdProcessor.class, 1, 406, 228)
                .proc(IdProcessor.class, 2, 626, 408)
                .proc(IdProcessor.class, 3, 415, 730)
                .proc(SubgraphPayload.class, 0, 195, 418)
                .merge(IdProcessor.class, 1, 475, 342)
                .merge(SubgraphPayload.class, 0, 341, 565)
                .merge(IdProcessor.class, 2, 488, 620)
                .merge(IdProcessor.class, 3, 490, 840)
                .start(460, 120)
                .complete(IdProcessor.class, 3, 460, 930)

                .buildGraph();

        printGraph(childGraph, parentGraph);

        reactor.registerReactorGraph(parentGraph);

        ParentGraphPayload resultPaylaod = reactor.submit(new ParentGraphPayload()).getResultFuture().get(5, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(1, 11, 12, 13, 2, 3), resultPaylaod.getIdSequence());
    }

    @Reactored({
            "Test demonstrates usage of mocked processor instead of real one.",
            "Test will check that single processor id end up at payloads idList.",
            "Expected result: {42}"
    })
    static class SingleInterfaceProcessorPayload extends IdListPayload {}

    @Test
    public void use_interface_mock_as_processor_with_mockito() throws Exception {

        IdProcessorInterface processorInterface = Mockito.mock(IdProcessorInterface.class);
        Mockito.when(processorInterface.handle()).thenReturn(CompletableFuture.completedFuture(42));


        Processor<SingleInterfaceProcessorPayload> idProcessor1 = graphBuilder.processor()
                .forPayload(SingleInterfaceProcessorPayload.class)
                .withHandler(processorInterface::handle)
                .withMerger((pld, id) -> {
                    pld.getIdSequence().add(id);
                    return Status.OK;
                })
                .buildProcessor();

        ReactorGraph<SingleInterfaceProcessorPayload> graph = graphBuilder.payload(SingleInterfaceProcessorPayload.class)
                
                .handleBy(idProcessor1)

                .mergePoint(idProcessor1)
                .onAny().complete()

                .coordinates()
                .proc(IdProcessorInterface.class, 0, 450, 184)
                .merge(IdProcessorInterface.class, 0, 522, 299)
                .start(500, 100)
                .complete(IdProcessorInterface.class, 0, 498, 398)

                .buildGraph();

        printGraph(graph);

        reactor.registerReactorGraph(graph);

        SingleInterfaceProcessorPayload resultPayload = reactor.submit(new SingleInterfaceProcessorPayload())
                .getResultFuture()
                .get(10, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(42), resultPayload.getIdSequence());
    }


    @Test
    public void use_class_mock_as_processor_with_mockito() throws Exception {

        IdProcessor processor = Mockito.mock(IdProcessor.class);
        Mockito.when(processor.handle()).thenReturn(CompletableFuture.completedFuture(78));

        Processor<SingleInterfaceProcessorPayload> idProcessor1 = graphBuilder.processor()
                .forPayload(SingleInterfaceProcessorPayload.class)
                .withHandler(processor::handle)
                .withMerger((pld, id) -> {
                    pld.getIdSequence().add(id);
                    return Status.OK;
                })
                .buildProcessor();

        ReactorGraph<SingleInterfaceProcessorPayload> graph = graphBuilder.payload(SingleInterfaceProcessorPayload.class)
                
                .handleBy(idProcessor1)

                .mergePoint(idProcessor1)
                .onAny().complete()

                .coordinates()
                .proc(IdProcessor.class, 0, 450, 184)
                .merge(IdProcessor.class, 0, 522, 299)
                .start(500, 100)
                .complete(IdProcessor.class, 0, 498, 398)

                .buildGraph();

        printGraph(graph);

        reactor.registerReactorGraph(graph);

        SingleInterfaceProcessorPayload resultPayload = reactor.submit(new SingleInterfaceProcessorPayload())
                .getResultFuture()
                .get(10, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(78), resultPayload.getIdSequence());
    }


    @Reactored({
            "Test will check that parallel processors work fine when only one of transitions are activated.",
            "Expected result: {0, 1}"
    })
    @Data
    static class DeadBranchPayload extends IdListPayload {
        ThreeStateStatus threeStateStatus;
    }

    enum ThreeStateStatus {A, B, AB}

    @Test
    public void parallel_processors_with_one_dead_branch_way() throws Exception {
        Processor<DeadBranchPayload> idProcessor0 = graphBuilder.processor()
                .forPayload(DeadBranchPayload.class)
                .withHandler(new IdProcessor(0)::handle)
                .withMerger((pld, id) -> {
                    pld.getIdSequence().add(id);
                    pld.setThreeStateStatus(ThreeStateStatus.A);
                    return ThreeStateStatus.A;
                })
                .buildProcessor()
                .setId(0);


        Processor<DeadBranchPayload> idProcessor1 = graphBuilder.processor()
                .forPayload(DeadBranchPayload.class)
                .withHandler(new IdProcessor(1)::handle)
                .withMerger((pld, id) -> {
                    pld.getIdSequence().add(id);
                    return pld.getThreeStateStatus();
                })
                .buildProcessor()
                .setId(1);

        Processor<DeadBranchPayload> idProcessor2 = graphBuilder.processor()
                .forPayload(DeadBranchPayload.class)
                .withHandler(new IdProcessor(2)::handle)
                .withMerger((pld, id) -> {
                    pld.getIdSequence().add(id);
                    return Status.OK;
                })
                .buildProcessor()
                .setId(2);

        ReactorGraph<DeadBranchPayload> graph = graphBuilder.payload(DeadBranchPayload.class)
                
                .handleBy(idProcessor0)

                .mergePoint(idProcessor0)
                .on(ThreeStateStatus.A).handleBy(idProcessor1)
                .on(ThreeStateStatus.B).handleBy(idProcessor2)
                .on(ThreeStateStatus.AB).handleBy(idProcessor1)
                .on(ThreeStateStatus.AB).handleBy(idProcessor2)


                .mergePoint(idProcessor1)
                .on(ThreeStateStatus.A).complete()
                .on(ThreeStateStatus.AB, ThreeStateStatus.B).merge(idProcessor2)


                .mergePoint(idProcessor2)
                .onAny().complete()

                .mergeGroup().with(idProcessor1).with(idProcessor2)

                .coordinates()

                .start(500, 100)

                .proc(IdProcessor.class, 0, 420, 210)
                .proc(IdProcessor.class, 1, 600, 420)
                .proc(IdProcessor.class, 2, 260, 420)

                .merge(IdProcessor.class, 0, 500, 320)
                .merge(IdProcessor.class, 1, 560, 510)
                .merge(IdProcessor.class, 2, 450, 540)

                .complete(IdProcessor.class, 1, 651, 595)
                .complete(IdProcessor.class, 2, 520, 660)

                .buildGraph();

        printGraph(graph);

        reactor.registerReactorGraph(graph);

        CompletableReactor.Execution<DeadBranchPayload> result = reactor.submit(new DeadBranchPayload());

        DeadBranchPayload resultPayload = result
                .getResultFuture()
                .get(10, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(0, 1), resultPayload.getIdSequence());
    }


    @Reactored({
            "Detached merge point works as an regular merge point ",
            "but there is no processor or subgraph or theirs result to merge.",
            "Merge point simply modify payload and send it through outgoing transitions.",
            "Expected result: {42, 1, 0}"
    })
    @Data
    static class DetachedMergePointFromStartPointPayload extends IdListPayload {}

    @Test
    public void detached_merge_point_from_start_point() throws Exception {
        final int MERGE_POINT_ID = 42;

        Processor<IdListPayload> idProcessor0 = buildProcessor(new IdProcessor(0)).setId(0);
        Processor<IdListPayload> idProcessor1 = buildProcessor(new IdProcessor(1)).setId(1);

        MergePoint<DetachedMergePointFromStartPointPayload> mergePoint = graphBuilder.mergePoint()
                .forPayload(DetachedMergePointFromStartPointPayload.class)
                .withMerger(
                        "mergePointTitle",
                        new String[]{
                                "merge point documentation",
                                "here"},
                        pld -> {
                            pld.getIdSequence().add(MERGE_POINT_ID);
                            return Status.OK;
                        })
                .buildMergePoint()
                .setId(0);


        ReactorGraph<DetachedMergePointFromStartPointPayload> graph = graphBuilder.payload(DetachedMergePointFromStartPointPayload.class)
                
                .handleBy(idProcessor0)
                .handleBy(idProcessor1)
                .merge(mergePoint)

                .mergePoint(mergePoint)
                .onAny().merge(idProcessor1)

                .mergePoint(idProcessor1)
                .onAny().merge(idProcessor0)

                .mergePoint(idProcessor0)
                .onAny().complete()

                .mergeGroup()
                .with(idProcessor0)
                .with(idProcessor1)
                .with(mergePoint)

                .coordinates()
                .start(107, 9)
                .proc(IdProcessor.class, 0, 22, 78)
                .proc(IdProcessor.class, 1, 211, 79)
                .merge(IdProcessor.class, 0, 126, 215)
                .merge(IdProcessor.class, 1, 267, 187)
                .merge(0, 424, 160)
                .complete(IdProcessor.class, 0, 57, 274)

                .buildGraph();

        printGraph(graph);

        reactor.registerReactorGraph(graph);

        CompletableReactor.Execution<DetachedMergePointFromStartPointPayload> result = reactor.submit(new DetachedMergePointFromStartPointPayload());

        DetachedMergePointFromStartPointPayload resultPayload = result
                .getResultFuture()
                .get(10, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(42, 1, 0), resultPayload.getIdSequence());
    }

    @Reactored({
            "Detached merge point works as an regular merge point ",
            "but there is no processor or subgraph or theirs result to merge.",
            "Merge point simply modify payload and send it through outgoing transitions.",
            "Expected result: {0, 1, 42}"

    })
    @Data
    static class DetachedMergePointFromProcessorsMergePointPayload extends IdListPayload {}

    @Test
    public void detached_merge_point_from_processors_merge_point() throws Exception {
        final int MERGE_POINT_ID = 42;

        Processor<IdListPayload> idProcessor0 = buildProcessor(new IdProcessor(0)).setId(0);
        Processor<IdListPayload> idProcessor1 = buildProcessor(new IdProcessor(1)).setId(1);

        MergePoint<DetachedMergePointFromProcessorsMergePointPayload> mergePoint = graphBuilder.mergePoint()
                .forPayload(DetachedMergePointFromProcessorsMergePointPayload.class)
                .withMerger(
                        "addMergePointId",
                        new String[]{
                                "Adds merge point id",
                                "to payload sequence"},
                        pld -> {
                            pld.getIdSequence().add(MERGE_POINT_ID);
                            return Status.OK;
                        })

                .buildMergePoint()
                .setId(0);

        ReactorGraph<DetachedMergePointFromProcessorsMergePointPayload> graph = graphBuilder.payload(DetachedMergePointFromProcessorsMergePointPayload.class)
                
                .handleBy(idProcessor0)
                .handleBy(idProcessor1)

                .mergePoint(idProcessor0)
                .onAny().merge(idProcessor1)

                .mergePoint(idProcessor1)
                .onAny().merge(mergePoint)

                .mergePoint(mergePoint)
                .onAny().complete()

                .mergeGroup()
                .with(idProcessor0)
                .with(idProcessor1)
                .with(mergePoint)

                .coordinates()
                .start(95, 62)
                .proc(IdProcessor.class, 0, 164, 131)
                .proc(IdProcessor.class, 1, 330, 127)
                .merge(IdProcessor.class, 0, 235, 224)
                .merge(IdProcessor.class, 1, 357, 241)
                .merge(0, 461, 289)
                .complete(0, 406, 369)

                .buildGraph();

        printGraph(graph);

        reactor.registerReactorGraph(graph);

        CompletableReactor.Execution<DetachedMergePointFromProcessorsMergePointPayload> result = reactor.submit(new DetachedMergePointFromProcessorsMergePointPayload());

        DetachedMergePointFromProcessorsMergePointPayload resultPayload = result
                .getResultFuture()
                .get(10, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(0, 1, 42), resultPayload.getIdSequence());

    }


    @Reactored({
            "OptionalProcessorExecution shows how to use detached merge point to avoid unnecessary processor execution",
            "Expected result for right: {1 ,2}",
            "Expected result for left: {2}"
    })

    @Accessors(chain = true)
    @Data
    static class OptionalProcessorExecutionPayload extends IdListPayload {
        OPTIONAL_DECISION whereToGo;
    }

    enum OPTIONAL_DECISION {
        LEFT, RIGHT
    }

    @Test
    public void optional_processor_execution() throws Exception {

        Processor<IdListPayload> idProcessor1 = buildProcessor(new IdProcessor(1)).setId(1);
        Processor<IdListPayload> idProcessor2 = buildProcessor(new IdProcessor(2)).setId(2);

        MergePoint<OptionalProcessorExecutionPayload> mergePoint = graphBuilder.mergePoint()
                .forPayload(OptionalProcessorExecutionPayload.class)
                .withMerger(
                        "getWhereToGo",
                        new String[]{
                                "returns destination from payload"
                        },
                        pld -> {
                            return pld.getWhereToGo();
                        })
                .buildMergePoint()
                .setId(0);


        ReactorGraph<OptionalProcessorExecutionPayload> graph = graphBuilder.payload(OptionalProcessorExecutionPayload.class)
                
                .merge(mergePoint)

                .mergePoint(mergePoint)
                .on(OPTIONAL_DECISION.LEFT).handleBy(idProcessor2)
                .on(OPTIONAL_DECISION.RIGHT).handleBy(idProcessor1)

                .mergePoint(idProcessor1)
                .onAny().handleBy(idProcessor2)

                .mergePoint(idProcessor2)
                .onAny().complete()

                .coordinates()
                .start(0, 17)
                .proc(IdProcessor.class, 1, 189, 164)
                .proc(IdProcessor.class, 2, 46, 269)
                .merge(IdProcessor.class, 1, 221, 233)
                .merge(IdProcessor.class, 2, 81, 354)
                .merge(0, 114, 82)
                .complete(IdProcessor.class, 2, 87, 432)

                .buildGraph();

        printGraph(graph);

        reactor.registerReactorGraph(graph);

        CompletableReactor.Execution<OptionalProcessorExecutionPayload> result = reactor.submit(new OptionalProcessorExecutionPayload()
                .setWhereToGo(OPTIONAL_DECISION.RIGHT));

        OptionalProcessorExecutionPayload resultPayload = result
                .getResultFuture()
                .get(10, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(1, 2), resultPayload.getIdSequence());


        result = reactor.submit(new OptionalProcessorExecutionPayload()
                .setWhereToGo(OPTIONAL_DECISION.LEFT));


        resultPayload = result
                .getResultFuture()
                .get(10, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(2), resultPayload.getIdSequence());
    }

}