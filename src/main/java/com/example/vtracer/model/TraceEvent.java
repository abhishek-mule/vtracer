package com.example.vtracer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TraceEvent {
    private final String className;
    private final String methodName;
    private final String signature;
    private final long threadId;
    private final String threadName;
    private final long startTime;
    private long endTime;
    private long selfTime;
    private final TraceEvent parent;
    private final List<TraceEvent> children;
    private final int depth;
    private final String stackTrace;

    public TraceEvent(String className, String methodName, String signature,
                      long threadId, String threadName, long startTime,
                      TraceEvent parent, int depth) {
        this.className = className;
        this.methodName = methodName;
        this.signature = signature;
        this.threadId = threadId;
        this.threadName = threadName;
        this.startTime = startTime;
        this.parent = parent;
        this.children = new ArrayList<>();
        this.depth = depth;
        this.stackTrace = captureStackTrace();
    }

    private String captureStackTrace() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length > 5) {
            return stack[5].toString();
        }
        return "";
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setSelfTime(long selfTime) {
        this.selfTime = selfTime;
    }

    public void addChild(TraceEvent child) {
        this.children.add(child);
    }

    public long getDuration() {
        return endTime - startTime;
    }

    public String getFrameId() {
        return className + "." + methodName;
    }

    public String getFullyQualifiedName() {
        return className + "." + methodName + signature;
    }

    // Getters
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getSignature() { return signature; }
    public long getThreadId() { return threadId; }
    public String getThreadName() { return threadName; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public long getSelfTime() { return selfTime; }
    public TraceEvent getParent() { return parent; }
    public List<TraceEvent> getChildren() { return new ArrayList<>(children); }
    public int getDepth() { return depth; }
    public String getStackTrace() { return stackTrace; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TraceEvent)) return false;
        TraceEvent that = (TraceEvent) o;
        return startTime == that.startTime &&
               threadId == that.threadId &&
               Objects.equals(className, that.className) &&
               Objects.equals(methodName, that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName, threadId, startTime);
    }

    @Override
    public String toString() {
        return String.format("%s.%s (%d ms, depth=%d)",
                className, methodName, getDuration() / 1_000_000, depth);
    }
}