package com.example.vtracer.tracing.collector;

import com.example.vtracer.tracing.model.TraceEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects trace events with bounded buffer and adaptive sampling
 *
 * <p>Thread-safe, lock-free design using ThreadLocal and ConcurrentLinkedQueue
 */
public class CallTreeCollector {

  private static final int MAX_STACK_DEPTH = 512;
  private static final long ESTIMATED_OVERHEAD_NS_PER_CALL = 50;

  private final ThreadLocal<CallStack> stacks;
  private final Queue<TraceEvent> eventBuffer;
  private final int bufferCapacity;
  private final AtomicLong totalEnters;
  private final AtomicLong totalExits;
  private final AtomicLong droppedEvents;
  private final AtomicLong sampledEvents;
  private final AtomicLong currentSize;
  private final long startTime;

  public CallTreeCollector(int bufferCapacity) {
    this.bufferCapacity = bufferCapacity;
    this.stacks = ThreadLocal.withInitial(CallStack::new);
    this.eventBuffer = new ConcurrentLinkedQueue<>();
    this.totalEnters = new AtomicLong(0);
    this.totalExits = new AtomicLong(0);
    this.droppedEvents = new AtomicLong(0);
    this.sampledEvents = new AtomicLong(0);
    this.currentSize = new AtomicLong(0);
    this.startTime = System.nanoTime();
  }

  public void onMethodEnter(long threadId, String className, String methodName, long timestamp) {
    totalEnters.incrementAndGet();

    CallStack stack = stacks.get();

    // Check max depth to prevent runaway recursion
    if (stack.depth() >= MAX_STACK_DEPTH) {
      droppedEvents.incrementAndGet();
      stack.pushNoOp();
      return;
    }

    CallFrame frame = new CallFrame(className, methodName, timestamp);
    stack.push(frame);

    // Create ENTER event
    TraceEvent event = TraceEvent.enter(threadId, className, methodName, timestamp);
    offerEvent(event);
  }

  public void onMethodExit(long threadId, long timestamp) {
    totalExits.incrementAndGet();

    CallStack stack = stacks.get();
    CallFrame frame = stack.pop();

    if (frame == null) {
      // Unmatched exit (shouldn't happen with proper instrumentation)
      return;
    }

    if (frame.isNoOp()) {
      // Was over depth limit
      return;
    }

    // Create EXIT event
    TraceEvent event =
        TraceEvent.exit(threadId, frame.getClassName(), frame.getMethodName(), timestamp);
    offerEvent(event);
  }

  private void offerEvent(TraceEvent event) {
    // Check buffer capacity using precise atomic counter
    if (currentSize.get() < bufferCapacity) {
      eventBuffer.offer(event);
      currentSize.incrementAndGet();
      sampledEvents.incrementAndGet();
    } else {
      droppedEvents.incrementAndGet();
    }
  }

  /** Drain all events from buffer */
  public List<TraceEvent> drain() {
    List<TraceEvent> events = new ArrayList<>();
    TraceEvent event;

    while ((event = eventBuffer.poll()) != null) {
      events.add(event);
    }

    sampledEvents.addAndGet(-events.size());
    currentSize.addAndGet(-events.size());

    return events;
  }

  /** Calculate current overhead percentage */
  public double getOverheadPercent() {
    long totalEvents = totalEnters.get() + totalExits.get();
    if (totalEvents == 0) {
      return 0.0;
    }

    long elapsedNanos = System.nanoTime() - startTime;
    if (elapsedNanos == 0) {
      return 0.0;
    }

    // Rough estimate: ESTIMATED_OVERHEAD_NS_PER_CALL per instrumented call
    long overheadNanos = totalEvents * ESTIMATED_OVERHEAD_NS_PER_CALL;

    return (double) overheadNanos / elapsedNanos;
  }

  /** Export metrics to file */
  public void exportMetrics(Path metricsFile) {
    try {
      Files.createDirectories(metricsFile.getParent());

      try (BufferedWriter w = Files.newBufferedWriter(metricsFile)) {
        w.write("methodEnters: " + totalEnters.get() + "\n");
        w.write("methodExits: " + totalExits.get() + "\n");
        w.write("eventsDropped: " + droppedEvents.get() + "\n");
        w.write("eventsSampled: " + sampledEvents.get() + "\n");
        w.write("overheadPercent: " + String.format("%.2f", getOverheadPercent() * 100) + "\n");
        w.write(
            "bufferUtilization: "
                + String.format("%.2f", (double) currentSize.get() / bufferCapacity * 100)
                + "\n");
      }
    } catch (IOException e) {
      System.err.println("[VTracer] Failed to export metrics: " + e.getMessage());
    }
  }

  /** Cleanup thread-local storage */
  public void cleanup() {
    stacks.remove();
  }

  /** Call frame on the stack */
  private static class CallFrame {
    private final String className;
    private final String methodName;
    private final long timestamp;
    private final boolean noOp;

    public CallFrame(String className, String methodName, long timestamp) {
      this.className = className;
      this.methodName = methodName;
      this.timestamp = timestamp;
      this.noOp = false;
    }

    private CallFrame() {
      this.className = null;
      this.methodName = null;
      this.timestamp = 0;
      this.noOp = true;
    }

    public static CallFrame noOp() {
      return new CallFrame();
    }

    public String getClassName() {
      return className;
    }

    public String getMethodName() {
      return methodName;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public boolean isNoOp() {
      return noOp;
    }
  }

  /** Thread-local call stack with recursion detection */
  private static class CallStack {
    private final Deque<CallFrame> frames;
    private final Set<String> activeFrames;

    public CallStack() {
      this.frames = new ArrayDeque<>();
      this.activeFrames = new HashSet<>();
    }

    public void push(CallFrame frame) {
      String sig = frame.getClassName() + "." + frame.getMethodName();

      // Detect direct recursion
      if (activeFrames.contains(sig)) {
        // For recursive calls, still track but mark specially
        // (Could enhance to track recursion depth)
      }

      frames.push(frame);
      if (!frame.isNoOp()) {
        activeFrames.add(sig);
      }
    }

    public void pushNoOp() {
      frames.push(CallFrame.noOp());
    }

    public CallFrame pop() {
      if (frames.isEmpty()) {
        return null;
      }

      CallFrame frame = frames.pop();

      if (!frame.isNoOp()) {
        String sig = frame.getClassName() + "." + frame.getMethodName();
        // Only remove if not recursive (top-level exit)
        boolean hasAnother =
            frames.stream()
                .anyMatch(
                    f -> !f.isNoOp() && sig.equals(f.getClassName() + "." + f.getMethodName()));
        if (!hasAnother) {
          activeFrames.remove(sig);
        }
      }

      return frame;
    }

    public int depth() {
      return frames.size();
    }
  }
}
