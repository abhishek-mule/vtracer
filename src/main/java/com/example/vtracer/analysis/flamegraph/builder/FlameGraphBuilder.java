package com.example.vtracer.analysis.flamegraph.builder;

import com.example.vtracer.analysis.calltree.CallTreeAnalyzer.CallTree;
import com.example.vtracer.analysis.flamegraph.model.FlameGraph;
import com.example.vtracer.analysis.flamegraph.model.ThreadFlameGraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Builds flame graphs from call trees
 *
 * <p>Algorithm: 1. Traverse call tree depth-first 2. Accumulate stack trace from root to leaf 3.
 * Emit stack trace with self-time at each leaf
 */
public class FlameGraphBuilder {

  /** Build flame graph from call tree */
  public static FlameGraph build(CallTree tree) {
    FlameGraph flameGraph = new FlameGraph();

    for (CallTree.Node root : tree.getRoots()) {
      buildFromNode(root, new ArrayDeque<>(), flameGraph);
    }

    return flameGraph;
  }

  /** Build per-thread flame graphs */
  public static ThreadFlameGraph buildPerThread(CallTree tree) {
    ThreadFlameGraph threadFlameGraph = new ThreadFlameGraph();

    for (CallTree.Node root : tree.getRoots()) {
      FlameGraph fg = threadFlameGraph.getOrCreateFlameGraph(root.getThreadId());
      buildFromNode(root, new ArrayDeque<>(), fg);
    }

    return threadFlameGraph;
  }

  private static void buildFromNode(
      CallTree.Node node, Deque<String> stack, FlameGraph flameGraph) {
    if (!node.isComplete()) {
      // Skip incomplete nodes
      return;
    }

    stack.addLast(node.getMethodSignature());

    // If this is a leaf node or has self-time, emit stack
    if (node.getSelfTime() > 0) {
      List<String> stackList = new ArrayList<>(stack);
      flameGraph.addSample(stackList, node.getSelfTime());
    }

    // Recurse to children
    for (CallTree.Node child : node.getChildren()) {
      buildFromNode(child, stack, flameGraph);
    }

    stack.removeLast();
  }
}
