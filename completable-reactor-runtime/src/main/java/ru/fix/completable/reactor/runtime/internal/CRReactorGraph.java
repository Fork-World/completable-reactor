package ru.fix.completable.reactor.runtime.internal;

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.val;
import ru.fix.completable.reactor.api.ReactorGraphModel;
import ru.fix.completable.reactor.api.Reactored;
import ru.fix.completable.reactor.runtime.ReactorGraph;
import ru.fix.completable.reactor.runtime.ReactorGraphBuilder;
import ru.fix.completable.reactor.runtime.dsl.Coordinates;
import ru.fix.completable.reactor.runtime.dsl.MergePointBuilder;
import ru.fix.completable.reactor.runtime.internal.dsl.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Model of graph used to generate execution chain.
 *
 * @author Kamil Asfandiyarov
 */
@Data
public class CRReactorGraph<PayloadType> implements ReactorGraph<PayloadType> {

    @Data
    @Accessors(chain = true)
    public static class MergeGroup {
        final List<MergePoint> mergePoints = new ArrayList<>();

        public MergeGroup(MergePoint... mergePoints) {
            this.mergePoints.addAll(Arrays.asList(mergePoints));
        }
    }

    @Data
    @Accessors(chain = true)
    public static class MergePoint {
        public enum Type {
            /**
             * Processors merge point
             */
            PROCESSOR,
            /**
             * Subgraphs merge point
             */
            SUBGRAPH,
            /**
             * Detached merge point without Processor or Subgraph
             */
            DETACHED
        }

        Type type = Type.PROCESSOR;

        /**
         * if type is PROCESSOR then Processor this merge point belong to otherwise NULL
         */
        CRProcessor processor;

        /**
         * if type is SUBGRAPH then Subgraph this merge point belong to otherwise NULL
         */
        CRSubgraph subgraph;

        /**
         * if type is DETACHED then MergePoint otherwise NULL
         */
        CRMergePoint mergePoint;


        ReactorGraphModel.Coordinates coordinates;
        ReactorGraphModel.Source coordinatesSource;

        final List<Transition> transitions = new ArrayList<>();

        public CRProcessingItem asProcessingItem() {
            switch (type) {
                case PROCESSOR:
                    return processor;
                case SUBGRAPH:
                    return subgraph;
                case DETACHED:
                    return mergePoint;
                default:
                    throw new IllegalArgumentException("Invalid type: " + type);
            }
        }

        public ReactorGraphModel.MergePoint serialize() {
            ReactorGraphModel.MergePoint model = new ReactorGraphModel.MergePoint();
            model.coordinates = this.coordinates != null ? this.coordinates : new ReactorGraphModel.Coordinates(100, 100);
            model.coordinatesSource = coordinatesSource;

            switch (this.getType()) {
                case PROCESSOR:
                    model.identity = this.processor.getIdentity();
                    model.mergerDocs = processor.getProcessorDescription().getMergerDocs();
                    model.mergerTitle = processor.getProcessorDescription().getMergerTitle();
                    model.mergeSource = processor.getProcessorDescription().getMergeSource();
                    break;
                case SUBGRAPH:
                    model.identity = this.subgraph.getIdentity();
                    model.mergerDocs = subgraph.getSubgraphDescription().getMergerDocs();
                    model.mergerTitle = subgraph.getSubgraphDescription().getMergerTitle();
                    model.mergeSource = subgraph.getSubgraphDescription().getMergeSource();
                    break;
                case DETACHED:
                    model.identity = this.mergePoint.getIdentity();
                    model.mergerDocs = mergePoint.getMergePointDescription().getMergerDocs();
                    model.mergerTitle = mergePoint.getMergePointDescription().getMergerTitle();
                    model.mergeSource = mergePoint.getMergePointDescription().getMergerSource();
                    break;
            }

            this.transitions.forEach(transition -> model.transitions.add(transition.serialize()));
            return model;
        }
    }

    @Data
    @Accessors(chain = true)
    public static class Transition {

        final Set<Enum<?>> mergeStatuses;

        boolean isOnAny;
        boolean isComplete = false;
        CRProcessingItem merge;
        CRProcessingItem handleBy;

        ReactorGraphModel.Coordinates completeCoordinates;
        ReactorGraphModel.Source completeCoordinatesSource;
        ReactorGraphModel.Source completeSource;

        ReactorGraphModel.Source transitionOnAnySource;
        final Map<String, ReactorGraphModel.Source> transitionOnStatusSource = new HashMap<>();
        final Map<String, ReactorGraphModel.Source> mergeStatusSources = new HashMap<>();


        private Map<String, String[]> transitionsDoc;

        public Transition(Enum<?>... mergeStatuses) {
            this.mergeStatuses = new HashSet<>(Arrays.asList(mergeStatuses));

            for (Enum<?> status : mergeStatuses) {
                mergeStatusSources.put(status.name(), new ReactorGraphModel.Source().setClassName(status.getClass().getName()));
            }
        }

        public ReactorGraphModel.Transition serialize() {
            ReactorGraphModel.Transition model = new ReactorGraphModel.Transition();
            model.mergeStatuses = Optional.ofNullable(mergeStatuses)
                    .map(Set::stream)
                    .map(stream -> stream.map(Enum::toString).collect(Collectors.toList()))
                    .orElse(null);
            model.isComplete = isComplete;
            model.isOnAny = isOnAny;

            model.mergeProcessingItem = Optional.ofNullable(merge).map(CRProcessingItem::getIdentity).orElse(null);

            model.handleByProcessingItem = Optional.ofNullable(handleBy).map(CRProcessingItem::getIdentity).orElse(null);

            model.completeCoordinates = completeCoordinates != null ? completeCoordinates : new ReactorGraphModel.Coordinates(100, 100);

            model.completeCoordinatesSource = completeCoordinatesSource;

            model.completeSource = completeSource;

            model.transitionOnAnySource = transitionOnAnySource;
            model.transitionOnStatusSource = transitionOnStatusSource;
            model.mergeStatusSources = mergeStatusSources;

            if (this.transitionsDoc != null) {
                this.transitionsDoc.forEach((status, docs) -> {
                    ReactorGraphModel.TransitionDocumentation doc = new ReactorGraphModel.TransitionDocumentation();
                    doc.mergeStatus = status;
                    doc.docs = docs;
                    model.transitionsDoc.add(doc);
                });
            }

            return model;
        }

    }

    public enum ProcessingItemType {
        PROCESSOR, SUBGRAPH, MERGE_POINT
    }

    @Data
    @Accessors(chain = true)
    public static class ProcessingItemInfo {

        ProcessingItemType processingItemType = ProcessingItemType.PROCESSOR;

        CRProcessorDescription description;
        CRSubgraphDescription subgraphDescription;
        CRMergePointDescription detachedMergePointDescription;

        ReactorGraphModel.Coordinates coordinates;

        ReactorGraphModel.Source coordinatesSource;

        boolean isMergerExist() {
            switch (processingItemType) {
                case PROCESSOR:
                    return description.getMerger() != null;
                case SUBGRAPH:
                    return subgraphDescription.getMerger() != null;
                case MERGE_POINT:
                    return detachedMergePointDescription.getMerger() != null;
                default:
                    throw new IllegalArgumentException(String.format("Invalid processing item type: %s", processingItemType));
            }
        }

        public ReactorGraphModel.Processor serializeProcessor() {
            if (processingItemType != ProcessingItemType.PROCESSOR) {
                throw new IllegalArgumentException("Invalid type: " + processingItemType);
            }

            ReactorGraphModel.Processor model = new ReactorGraphModel.Processor();
            model.coordinates = coordinates != null ? coordinates : new ReactorGraphModel.Coordinates(100, 100);
            model.coordinatesSource = this.coordinatesSource;

            model.processorDoc = description.getProcessorDoc();
            model.handlerDoc = description.getHandlerDocs();
            model.handlerTitle = description.getHandlerTitle();

            model.withHandlerSource = Optional.ofNullable(this.description)
                    .map(CRProcessorDescription::getWithHandlerSource)
                    .orElse(null);

            return model;
        }

        public ReactorGraphModel.Subgraph serializeSubgraph() {
            ReactorGraphModel.Subgraph model = new ReactorGraphModel.Subgraph();
            model.coordinates = coordinates != null ? coordinates : new ReactorGraphModel.Coordinates(100, 100);
            model.coordinatesSource = this.coordinatesSource;

            model.buildSource = subgraphDescription.getBuildSource();
            model.subgraphDoc = subgraphDescription.getSubgraphDoc();
            model.subgraphTitle = subgraphDescription.getSubgraphTitle();
            model.payloadClass = subgraphDescription.getSubgraphPayload().getName();

            return model;
        }
    }


    @Data
    @Accessors(chain = true)
    public static class StartPoint {

        ReactorGraphModel.Coordinates coordinates;
        ReactorGraphModel.Source coordinatesSource;

        /**
         * Where {@link ReactorGraphBuilder#payload(Class)} method was invoked during graph construction
         */
        ReactorGraphModel.Source builderPayloadSource;

        final List<CRProcessingItem> processingItems = new ArrayList<>();


        public ReactorGraphModel.StartPoint serialize() {
            ReactorGraphModel.StartPoint model = new ReactorGraphModel.StartPoint();
            model.coordinates = this.coordinates != null ? this.coordinates : new ReactorGraphModel.Coordinates(500, 100);
            model.coordinatesSource = this.coordinatesSource;

            model.setBuilderPayloadSource(this.getBuilderPayloadSource());

            this.processingItems.stream()
                    .map(CRProcessingItem::getIdentity)
                    .sorted()
                    .forEach(model.processingItems::add);
            return model;
        }
    }

    /**
     * Graph Payload Type
     */
    Class<PayloadType> payloadClass;

    /**
     * Location where {@link MergePointBuilder#coordinates())} was invoked.
     */
    ReactorGraphModel.Source coordinatesSource;

    /**
     * Location where {@link Coordinates#buildGraph()} was invoked.
     */
    ReactorGraphModel.Source buildGraphSource;

    @Getter
    final Map<CRProcessingItem, ProcessingItemInfo> processingItems = new HashMap<>();

    @Getter
    final List<MergeGroup> mergeGroups = new ArrayList<>();

    @Getter
    Optional<MergeGroup> startPointMergeGroup;

    @Getter
    final List<MergePoint> mergePoints = new ArrayList<>();

    @Getter
    final StartPoint startPoint = new CRReactorGraph.StartPoint();

    public CRReactorGraph(Class<PayloadType> payloadClass) {
        this.payloadClass = payloadClass;
    }

    ReactorGraphModel.Payload serializePayload() {
        ReactorGraphModel.Payload payload = new ReactorGraphModel.Payload();
        payload.payloadClass = payloadClass.getName();
        payload.payloadName = payloadClass.getSimpleName();

        val paylaodDocBuilder = new ArrayList<String>();

        for (Class<?> clazz = payloadClass; clazz != null; clazz = clazz.getSuperclass()) {
            val docs = Optional.ofNullable(clazz.getDeclaredAnnotation(Reactored.class))
                    .map(Reactored::value)
                    .orElse(null);

            if (docs != null) {
                if (clazz != payloadClass) {//one of super classes
                    paylaodDocBuilder.add("extends " + clazz.getSimpleName());
                }
                paylaodDocBuilder.addAll(Arrays.asList(docs));
            }
        }
        payload.payloadDoc = paylaodDocBuilder.toArray(new String[paylaodDocBuilder.size()]);
        return payload;
    }

    @Override
    public ReactorGraphModel serialize() {
        ReactorGraphModel model = new ReactorGraphModel();

        model.payload = serializePayload();

        model.coordinatesSource = this.coordinatesSource;
        model.buildGraphSource = this.buildGraphSource;

        processingItems.entrySet().stream()
                .sorted(Comparator.comparing(entry -> serialize(entry.getKey())))
                .forEach(entry -> {
                    switch (entry.getValue().getProcessingItemType()) {
                        case PROCESSOR:
                            val processor = entry.getValue().serializeProcessor();
                            processor.setIdentity(entry.getKey().getIdentity());
                            model.processors.add(processor);
                            break;
                        case SUBGRAPH:
                            val subgraph = entry.getValue().serializeSubgraph();
                            subgraph.setIdentity(entry.getKey().getIdentity());
                            model.subgraphs.add(subgraph);
                            break;
                        case MERGE_POINT:
                            break;
                        default:
                            throw new IllegalStateException("Invalid type: " + entry.getValue().getProcessingItemType());


                    }
                });

        model.startPoint = startPoint.serialize();

        this.mergePoints.stream()
                .map(MergePoint::serialize)
                .forEach(model.mergePoints::add);

        /**
         * Serialize implicit MergeGroup information
         */
        this.mergeGroups.forEach(mergeGroup -> {
            val modelGroup = new ReactorGraphModel.MergeGroup();
            mergeGroup.getMergePoints().stream()
                    .map(MergePoint::asProcessingItem)
                    .map(CRProcessingItem::getIdentity)
                    .forEach(modelGroup.getMergePoints()::add);
            if(this.getStartPointMergeGroup().isPresent() && this.getStartPointMergeGroup().get() == mergeGroup) {
                modelGroup.setIncludesStartPoint(true);
            }
            model.getImplicitMergeGroups().add(modelGroup);
        });

        return model;
    }


    public static String serialize(CRProcessingItem CRProcessingItem) {
        if (CRProcessingItem instanceof CRProcessor) {
            return serialize((CRProcessor) CRProcessingItem);
        }
        if (CRProcessingItem instanceof CRSubgraph) {
            return serialize((CRSubgraph) CRProcessingItem);
        }
        if (CRProcessingItem instanceof CRMergePoint) {
            return serialize((CRMergePoint) CRProcessingItem);
        }
        throw new IllegalArgumentException(String.format("Instance of class %s not supported.", CRProcessingItem.getClass()));
    }

    public static String serialize(CRProcessor graphProcessor) {
        if (graphProcessor == null) {
            return null;
        }

        return String.format("%s@%d",
                graphProcessor.getProcessorDescription().getProcessorType().getSimpleName(),
                graphProcessor.getId());
    }

    public static String serialize(Class processorType, int id) {
        return String.format("%s@%d",
                processorType.getSimpleName(),
                id);
    }

    public static String serializeMergePoint(int id) {
        return String.format("MergePoint@%d", id);
    }

    public static String serialize(CRMergePoint graphMergePoint) {
        return serializeMergePoint(graphMergePoint.getId());
    }


    public static String serialize(CRSubgraph subgraphProcessor) {
        return subgraphProcessor == null ? null :
                String.format("%s@%d",
                        subgraphProcessor.getPayloadClass().getSimpleName(),
                        subgraphProcessor.getId());
    }


    public void ensureProcessingItemRegistered(CRMergePoint graphMergePoint) {
        Objects.requireNonNull(graphMergePoint);

        if (this.getProcessingItems().keySet().stream()
                .anyMatch(item -> item.getIdentity().equals(graphMergePoint.getIdentity()) && item != graphMergePoint)) {
            throw new IllegalArgumentException(String.format(
                    "There are two MergePoint object with same id but different reference: %s." +
                            " It could lead to ambiguous graph structure declaration.",
                    graphMergePoint.getDebugName()));
        }

        if (this.getProcessingItems().containsKey(graphMergePoint)) {
            return;
        }

        CRReactorGraph.ProcessingItemInfo processorInfo = new CRReactorGraph.ProcessingItemInfo()
                .setProcessingItemType(CRReactorGraph.ProcessingItemType.MERGE_POINT)
                .setDetachedMergePointDescription(graphMergePoint.getMergePointDescription());

        this.getProcessingItems().put(graphMergePoint, processorInfo);
    }

    public void ensureProcessingItemRegistered(CRProcessor<?> graphProcessor) {
        Objects.requireNonNull(graphProcessor);

        if (this.getProcessingItems().keySet().stream()
                .anyMatch(item -> item.getIdentity().equals(graphProcessor.getIdentity()) && item != graphProcessor)) {
            throw new IllegalArgumentException(String.format(
                    "There are two Processor object with same id but different reference: %s." +
                            " It could lead to ambiguous graph structure declaration. E.g.:\n" +
                            "processor1 = ...buildProcessor().setId(1);\n" +
                            "processor2 = ...buildProcessor().setId(2);",
                    graphProcessor.getDebugName()));
        }

        if (this.getProcessingItems().containsKey(graphProcessor)) {
            return;
        }

        CRReactorGraph.ProcessingItemInfo processorInfo = new CRReactorGraph.ProcessingItemInfo();
        processorInfo.setDescription(graphProcessor.getProcessorDescription());

        this.getProcessingItems().put(graphProcessor, processorInfo);
    }

    public void ensureProcessingItemRegistered(CRSubgraph<?> subgraphProcessor) {
        Objects.requireNonNull(subgraphProcessor);

        if (this.getProcessingItems().keySet().stream()
                .anyMatch(item -> item.getIdentity().equals(subgraphProcessor.getIdentity()) && item != subgraphProcessor)) {
            throw new IllegalArgumentException(String.format(
                    "There are two Subgraph object with same id but different reference: %s." +
                            " It could lead to ambiguous graph structure declaration.",
                    subgraphProcessor.getDebugName()));
        }

        if (this.getProcessingItems().containsKey(subgraphProcessor)) {
            return;
        }

        CRReactorGraph.ProcessingItemInfo processorInfo = new CRReactorGraph.ProcessingItemInfo()
                .setProcessingItemType(CRReactorGraph.ProcessingItemType.SUBGRAPH)
                .setSubgraphDescription(subgraphProcessor.getSubgraphDescription());

        this.getProcessingItems().put(subgraphProcessor, processorInfo);
    }

}
