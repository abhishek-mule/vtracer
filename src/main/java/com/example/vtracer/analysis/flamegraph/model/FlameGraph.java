package com.example.vtracer.analysis.flamegraph.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Flame graph representation
 *
 * <p>Stores folded stacks with accumulated time
 */
public class FlameGraph {

  private final Map<String, Long> foldedStacks;

  public FlameGraph() {
    this.foldedStacks = new HashMap<>();
  }

  /**
   * Add a stack sample with given weight
   *
   * @param stack stack trace as list of method signatures (root to leaf)
   * @param weight time in nanoseconds
   */
  public void addSample(java.util.List<String> stack, long weight) {
    if (stack.isEmpty() || weight == 0) {
      return;
    }

    String folded = String.join(";", stack);
    foldedStacks.merge(folded, weight, Long::sum);
  }

  /** Get folded stacks map */
  public Map<String, Long> getFoldedStacks() {
    return foldedStacks;
  }

  /** Get total time across all stacks */
  public long getTotalTime() {
    return foldedStacks.values().stream().mapToLong(Long::longValue).sum();
  }
}
