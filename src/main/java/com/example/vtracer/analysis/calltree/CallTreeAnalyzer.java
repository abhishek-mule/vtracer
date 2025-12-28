package com.example.vtracer.analysis.calltree;

import com.example.vtracer.tracing.model.TraceEvent;
import java.util.*;

/**
 * Builds call trees from trace events
 *
 * <p>Handles: - Multi-threaded traces (separate trees per thread) - Unmatched exits (exception
 * unwinding) - Recursion detection
 */
public class CallTreeAnalyzer {

  /** Build call tree from trace events */
  public CallTree buildTree(List<TraceEvent> events) {
    CallTree tree = new CallTree();

    // Group events by thread
    Map<Long, List<TraceEvent>> eventsByThread = new HashMap<>();
    for (TraceEvent event : events) {
      eventsByThread.computeIfAbsent(event.getThreadId(), k -> new ArrayList<>()).add(event);
    }

    // Build tree for each thread
    for (Map.Entry<Long, List<TraceEvent>> entry : eventsByThread.entrySet()) {
      long threadId = entry.getKey();
      List<TraceEvent> threadEvents = entry.getValue();

      buildThreadTree(tree, threadId, threadEvents);
    }

    return tree;
  }

  private void buildThreadTree(CallTree tree, long threadId, List<TraceEvent> events) {
    Deque<CallTree.Node> stack = new ArrayDeque<>();

    for (TraceEvent event : events) {
      if (event.isEnter()) {
        CallTree.Node node =
            new CallTree.Node(
                event.getMethodSignature(),
                event.getClassName(),
                event.getMethodName(),
                event.getTimestamp(),
                threadId);

        if (stack.isEmpty()) {
          // Root node
          tree.addRoot(node);
        } else {
          // Child node
          stack.peek().addChild(node);
        }

        stack.push(node);

      } else if (event.isExit()) {
        if (stack.isEmpty()) {
          // Unmatched exit - log but continue
          System.err.printf(
              "[VTracer] Unmatched EXIT for %s at %d%n",
              event.getMethodSignature(), event.getTimestamp());
          continue;
        }

        CallTree.Node node = stack.pop();

        // Validate method matches
        if (!node.getMethodSignature().equals(event.getMethodSignature())) {
          System.err.printf(
              "[VTracer] Method mismatch: expected %s, got %s%n",
              node.getMethodSignature(), event.getMethodSignature());
          // Still set end time to be resilient
        }

        node.setEndTime(event.getTimestamp());
      }
    }

    // Handle unmatched enters (shouldn't happen with proper instrumentation)
    if (!stack.isEmpty()) {
      System.err.printf(
          "[VTracer] %d unmatched ENTER events in thread %d%n", stack.size(), threadId);
    }
  }

  /** Call tree structure */
  public static class CallTree {
    private final List<Node> roots;

    public CallTree() {
      this.roots = new ArrayList<>();
    }

    public void addRoot(Node node) {
      roots.add(node);
    }

    public List<Node> getRoots() {
      return roots;
    }

    /** Tree node representing a method invocation */
    public static class Node {
      private final String methodSignature;
      private final String className;
      private final String methodName;
      private final long startTime;
      private final long threadId;
      private long endTime;
      private final List<Node> children;
      private long totalTime; // Including children
      private long selfTime; // Excluding children

      public Node(
          String methodSignature,
          String className,
          String methodName,
          long startTime,
          long threadId) {
        this.methodSignature = methodSignature;
        this.className = className;
        this.methodName = methodName;
        this.startTime = startTime;
        this.threadId = threadId;
        this.endTime = -1;
        this.children = new ArrayList<>();
        this.totalTime = 0;
        this.selfTime = 0;
      }

      public void addChild(Node child) {
        children.add(child);
      }

      public void setEndTime(long endTime) {
        this.endTime = endTime;
        this.totalTime = endTime - startTime;
      }

      // Getters
      public String getMethodSignature() {
        return methodSignature;
      }

      public String getClassName() {
        return className;
      }

      public String getMethodName() {
        return methodName;
      }

      public long getStartTime() {
        return startTime;
      }

      public long getEndTime() {
        return endTime;
      }

      public long getThreadId() {
        return threadId;
      }

      public List<Node> getChildren() {
        return children;
      }

      public long getTotalTime() {
        return totalTime;
      }

      public long getSelfTime() {
        return selfTime;
      }

      public void setSelfTime(long selfTime) {
        this.selfTime = selfTime;
      }

      public boolean isComplete() {
        return endTime != -1;
      }
    }
  }
}
