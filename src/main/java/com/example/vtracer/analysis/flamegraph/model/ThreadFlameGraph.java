package com.example.vtracer.analysis.flamegraph.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Flame graphs per thread
 */
public class ThreadFlameGraph {

    private final Map<Long, FlameGraph> flameGraphsByThread;

    public ThreadFlameGraph() {
        this.flameGraphsByThread = new HashMap<>();
    }

    public FlameGraph getOrCreateFlameGraph(long threadId) {
        return flameGraphsByThread.computeIfAbsent(threadId, k -> new FlameGraph());
    }

    public Map<Long, FlameGraph> getFlameGraphsByThread() {
        return flameGraphsByThread;
    }

    /**
     * Merge all thread flame graphs into one
     */
    public FlameGraph merge() {
        FlameGraph merged = new FlameGraph();

        for (FlameGraph fg : flameGraphsByThread.values()) {
            for (Map.Entry<String, Long> entry : fg.getFoldedStacks().entrySet()) {
                merged.addSample(
                        java.util.Arrays.asList(entry.getKey().split(";")),
                        entry.getValue()
                );
            }
        }

        return merged;
    }
}