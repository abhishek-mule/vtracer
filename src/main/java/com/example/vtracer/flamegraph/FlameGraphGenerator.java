package com.example.vtracer.flamegraph;

import com.example.vtracer.model.TraceEvent;
import java.util.*;

public class FlameGraphGenerator {

    public static FlameGraphData generate(Map<Long, List<TraceEvent>> roots) {
        FlameGraphData data = new FlameGraphData();
        data.setStartTime(System.nanoTime());

        for (Map.Entry<Long, List<TraceEvent>> entry : roots.entrySet()) {
            long threadId = entry.getKey();
            List<TraceEvent> threadRoots = entry.getValue();

            ThreadFlameGraph threadGraph = new ThreadFlameGraph(threadId);

            for (TraceEvent root : threadRoots) {
                processEvent(root, threadGraph, "");
            }

            data.addThreadGraph(threadGraph);
        }

        data.setEndTime(System.nanoTime());
        data.setTotalSamples(roots.values().stream()
                .mapToInt(List::size)
                .sum());

        return data;
    }

    private static void processEvent(TraceEvent event,
                                     ThreadFlameGraph graph,
                                     String stackPrefix) {
        String currentStack = stackPrefix.isEmpty()
                ? event.getFrameId()
                : stackPrefix + ";" + event.getFrameId();

        graph.addFrame(
                currentStack,
                event.getSelfTime(),
                event.getDuration(),
                event.getDepth()
        );

        for (TraceEvent child : event.getChildren()) {
            processEvent(child, graph, currentStack);
        }
    }
}

class ThreadFlameGraph {
    private final long threadId;
    private final Map<String, FrameData> frames = new LinkedHashMap<>();

    public ThreadFlameGraph(long threadId) {
        this.threadId = threadId;
    }

    public void addFrame(String stack, long selfTime, long totalTime, int depth) {
        frames.put(stack, new FrameData(stack, selfTime, totalTime, depth));
    }

    public long getThreadId() {
        return threadId;
    }

    public Map<String, FrameData> getFrames() {
        return frames;
    }
}

class FrameData {
    private final String stack;
    private final long selfTime;
    private final long totalTime;
    private final int depth;

    public FrameData(String stack, long selfTime, long totalTime, int depth) {
        this.stack = stack;
        this.selfTime = selfTime;
        this.totalTime = totalTime;
        this.depth = depth;
    }

    public String[] getStackFrames() {
        return stack.split(";");
    }

    public String getLeafFrame() {
        String[] frames = getStackFrames();
        return frames[frames.length - 1];
    }

    public String getStack() { return stack; }
    public long getSelfTime() { return selfTime; }
    public long getTotalTime() { return totalTime; }
    public int getDepth() { return depth; }
}