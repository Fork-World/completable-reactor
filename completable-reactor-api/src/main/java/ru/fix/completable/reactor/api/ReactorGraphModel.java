package ru.fix.completable.reactor.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON representation of reactor graph.
 * @author Kamil Asfandiyarov
 */
@Data
@Accessors(chain = true)
public class ReactorGraphModel {

    @Data
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class Coordinates{
        public int x;
        public int y;
    }

    @Data
    @Accessors(chain = true)
    public static class Payload {
        public String payloadName;
        public String payloadClass;
        public String[] payloadDoc;
    }

    @Data
    @Accessors(chain = true)
    public static class StartPoint {
        public Coordinates coordinates;
        public Source coordinatesSource;
        public final List<String> processingItems = new ArrayList<>();
    }

    @Data
    @Accessors(chain = true)
    public static class MergeGroup {
        public final List<MergePoint> mergePoints = new ArrayList<>();
    }

    @Data
    @Accessors(chain = true)
    public static class MergePoint {
        public String processor;
        public String subgraph;
        public String id;
        public Coordinates coordinates;
        public Source coordinatesSource;
        public Source mergeSource;
        public String mergerTitle;
        public String[] mergerDocs;
        public final List<Transition> transitions = new ArrayList<>();
    }

    @Data
    @Accessors(chain = true)
    public static class TransitionDocumentation{
        public String mergeStatus;
        public String[] docs;
    }

    @Data
    @Accessors(chain = true)
    public static class Transition {
        public List<String> mergeStatuses;
        public boolean isOnAny;
        public boolean isComplete;
        public String mergeProcessingItem;
        public String handleByProcessingItem;
        public Coordinates completeCoordinates;
        public Source completeCoordinatesSource;
        public Source completeSource;
        public final List<TransitionDocumentation> transitionsDoc = new ArrayList<>();

        public ReactorGraphModel.Source transitionOnAnySource;
        public Map<String, ReactorGraphModel.Source> transitionOnStatusSource;
        public Map<String, ReactorGraphModel.Source> mergeStatusSources;
    }

    @Data
    @Accessors(chain = true)
    public static class Source {
        public String className;
        public String fileName;
        public Integer fileNameLine;
    }

    @Data
    @Accessors(chain = true)
    public static class Processor{
        public String id;

        public Coordinates coordinates;
        public Source coordinatesSource;

        public String[] processorDoc;
        public String handlerTitle;
        public String[] handlerDoc;

        public Source withHandlerSource;
    }

    @Data
    @Accessors(chain = true)
    public static class Subgraph{
        public String id;

        public Coordinates coordinates;
        public Source coordinatesSource;

        public String subgraphTitle;
        public String[] subgraphDoc;
    }

    public Payload payload;
    public StartPoint startPoint;

    public final List<Processor> processors = new ArrayList<>();
    public final List<Subgraph> subgraphs = new ArrayList<>();

    public final List<MergeGroup> mergeGroups = new ArrayList<>();

    public Source serializationPointSource;
    public Source coordinatesSource;
    public Source buildGraphSource;
}
