package com.example.vtracer.flamegraph;

import java.util.*;

public class FlameGraphData {
    private final List<ThreadFlameGraph> threadGraphs = new ArrayList<>();
    private long totalSamples;
    private long startTime;
    private long endTime;
    private final Map<String, FrameStats> aggregatedStats = new HashMap<>();

    public void addThreadGraph(ThreadFlameGraph graph) {
        threadGraphs.add(graph);
    }

    public List<ThreadFlameGraph> getThreadGraphs() {
        return new ArrayList<>(threadGraphs);
    }

    public Map<String, FrameStats> getAggregatedFrames() {
        if (aggregatedStats.isEmpty()) {
            computeAggregatedStats();
        }
        return new HashMap<>(aggregatedStats);
    }

    private void computeAggregatedStats() {
        for (ThreadFlameGraph tg : threadGraphs) {
            for (Map.Entry<String, FrameData> entry : tg.getFrames().entrySet()) {
                aggregatedStats.computeIfAbsent(entry.getKey(), k -> new FrameStats())
                        .merge(entry.getValue());
            }
        }
    }

    public long getTotalDuration() {
        return threadGraphs.stream()
                .flatMap(tg -> tg.getFrames().values().stream())
                .mapToLong(FrameData::getTotalTime)
                .sum();
    }

    public int getTotalFrameCount() {
        return threadGraphs.stream()
                .mapToInt(tg -> tg.getFrames().size())
                .sum();
    }

    public int getMaxDepth() {
        return threadGraphs.stream()
                .flatMap(tg -> tg.getFrames().values().stream())
                .mapToInt(FrameData::getDepth)
                .max()
                .orElse(0);
    }

    // Setters
    public void setTotalSamples(long totalSamples) { this.totalSamples = totalSamples; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    // Getters
    public long getTotalSamples() { return totalSamples; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
}

class FrameStats {
    private long totalTime;
    private long selfTime;
    private int count;

    public void merge(FrameData data) {
        this.totalTime += data.getTotalTime();
        this.selfTime += data.getSelfTime();
        this.count++;
    }

    public long getTotalTime() { return totalTime; }
    public long getSelfTime() { return selfTime; }
    public int getCount() { return count; }
    public long getAverageTime() { return count == 0 ? 0 : totalTime / count; }
}