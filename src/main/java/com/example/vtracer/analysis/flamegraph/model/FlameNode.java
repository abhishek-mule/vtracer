package com.example.vtracer.analysis.flamegraph.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Node in a flame graph
 *
 * Represents a stack frame with accumulated time
 */
public class FlameNode {

    private final String methodSignature;
    private long selfTime;
    private final List<FlameNode> children;

    public FlameNode(String methodSignature) {
        this.methodSignature = methodSignature;
        this.selfTime = 0;
        this.children = new ArrayList<>();
    }

    public void addSelfTime(long time) {
        this.selfTime += time;
    }

    public FlameNode getOrCreateChild(String methodSignature) {
        for (FlameNode child : children) {
            if (child.getMethodSignature().equals(methodSignature)) {
                return child;
            }
        }

        FlameNode newChild = new FlameNode(methodSignature);
        children.add(newChild);
        return newChild;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public long getSelfTime() {
        return selfTime;
    }

    public List<FlameNode> getChildren() {
        return children;
    }

    public long getTotalTime() {
        long total = selfTime;
        for (FlameNode child : children) {
            total += child.getTotalTime();
        }
        return total;
    }
}