package com.example.vtracer.analysis.flamegraph.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node in a flame graph
 *
 * <p>Represents a stack frame with accumulated time
 */
public class FlameNode {

  private final String methodSignature;
  private long selfTime;
  private final List<FlameNode> children;
  private final Map<String, FlameNode> childrenMap;

  public FlameNode(String methodSignature) {
    this.methodSignature = methodSignature;
    this.selfTime = 0;
    this.children = new ArrayList<>();
    this.childrenMap = new HashMap<>();
  }

  public void addSelfTime(long time) {
    this.selfTime += time;
  }

  public FlameNode getOrCreateChild(String methodSignature) {
    return childrenMap.computeIfAbsent(methodSignature, sig -> {
      FlameNode newChild = new FlameNode(sig);
      children.add(newChild);
      return newChild;
    });
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
