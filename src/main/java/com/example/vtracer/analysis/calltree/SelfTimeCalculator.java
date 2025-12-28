package com.example.vtracer.analysis.calltree;

import com.example.vtracer.analysis.calltree.CallTreeAnalyzer.CallTree;

/**
 * Calculates self-time for each node in call tree
 *
 * <p>Self-time = Total time - Sum of children's total time
 */
public class SelfTimeCalculator {

  /** Calculate self-time for all nodes in tree */
  public void calculate(CallTree tree) {
    for (CallTree.Node root : tree.getRoots()) {
      calculateNode(root);
    }
  }

  private void calculateNode(CallTree.Node node) {
    if (!node.isComplete()) {
      // Incomplete node (unmatched enter/exit)
      node.setSelfTime(0);
      return;
    }

    // Recursively calculate children first
    for (CallTree.Node child : node.getChildren()) {
      calculateNode(child);
    }

    // Self time = total time - sum of children's total time
    long childrenTime = node.getChildren().stream().mapToLong(CallTree.Node::getTotalTime).sum();

    long selfTime = node.getTotalTime() - childrenTime;

    // Guard against negative self-time (shouldn't happen but be defensive)
    if (selfTime < 0) {
      System.err.printf(
          "[VTracer] Negative self-time for %s: total=%d, children=%d%n",
          node.getMethodSignature(), node.getTotalTime(), childrenTime);
      selfTime = 0;
    }

    node.setSelfTime(selfTime);
  }
}
