# VTracer Architecture

## Design Philosophy

VTracer is architected as a **production-grade profiler** with the following principles:

1. **Separation of Concerns** - Each layer has a single responsibility
2. **One-Way Dependencies** - Strict dependency flow prevents cyclic coupling
3. **Minimal Overhead** - Lock-free data structures, bounded buffers, adaptive sampling
4. **Correctness First** - Handle recursion, exceptions, concurrency properly
5. **Observable** - Export metrics, validate invariants, fail safely

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      VTracerAgent                            │
│  Lifecycle: premain, agentmain, shutdown                     │
│  Holds: VTracerContext (singleton)                           │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                 InstrumentationSetup                         │
│  ByteBuddy: weave @Advice into filtered methods              │
│  Filters: exclude JDK, agent, user-specified classes         │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                  MethodAdvice                                │
│  Entry: Capture threadId, className, methodName, timestamp   │
│  Exit: Capture timestamp (even on exceptions)                │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                  CallTreeCollector                           │
│  ThreadLocal<CallStack> → ConcurrentQueue<TraceEvent>        │
│  Lock-free, bounded buffer, adaptive sampling                │
│  Metrics: enters, exits, dropped, overhead                   │
└────────────┬────────────────────────────────────────────────┘
             │ (Background thread drains every N seconds)
             ▼
┌─────────────────────────────────────────────────────────────┐
│                  CallTreeAnalyzer                            │
│  TraceEvent[] → CallTree                                     │
│  Handles: recursion, exceptions, multi-threading             │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                SelfTimeCalculator                            │
│  CallTree → CallTree with self-time annotations              │
│  Algorithm: totalTime - sum(children.totalTime)              │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                  FlameGraphBuilder                           │
│  CallTree → FlameGraph (folded stacks)                       │
│  Traverses tree depth-first, emits stack traces              │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                    Reporter                                  │
│  FlameGraph → File (folded, JSON)                            │
│  Async writes, never blocks event collection                 │
└─────────────────────────────────────────────────────────────┘
```

## Layer Details

### 1. Agent Layer (`agent/`)

**Responsibility**: JVM agent lifecycle

**Classes**:
- `VTracerAgent` - Entry point (premain/agentmain), holds singleton context
- `VTracerContext` - Coordinates all subsystems, owns background threads
- `AgentBootstrap` - Initializes instrumentation
- `AttachTool` - Dynamic attach utility

**Key Design Decisions**:
- **Only ONE static field** (`VTracerContext`) to avoid static state sprawl
- Context object is created once and passed explicitly everywhere
- Shutdown hook ensures clean termination (flush buffers, stop threads)
- Supports both premain (startup) and agentmain (dynamic attach)

### 2. Config Layer (`config/`)

**Responsibility**: Configuration parsing and validation

**Classes**:
- `VTracerConfig` - Immutable config object (builder pattern)

**Key Design Decisions**:
- Parse from agent args, environment variables, or config files
- Default to safe values (5% sampling, 2% overhead target)
- Validate constraints (buffer size must be power of 2, rates in [0,1])
- Provide ByteBuddy matchers for class/method filtering

### 3. Instrumentation Layer (`instrumentation/`)

**Responsibility**: Bytecode weaving

**Classes**:
- `InstrumentationSetup` - Configures ByteBuddy agent builder
- `MethodAdvice` - Entry/exit advice injected into methods

**Key Design Decisions**:
- **`inline = true`** - Zero allocation, no extra stack frames
- **`onThrowable = Throwable`** - Handle exceptions properly
- **Never throw from advice** - Catch all exceptions, log, continue
- **Pass context explicitly** - No static lookups in hot path
- **Filter aggressively** - Never instrument JDK, agent, or ByteBuddy classes

### 4. Tracing Layer (`tracing/`)

**Responsibility**: Event collection with sampling

**Classes**:
- `TraceEvent` - Immutable event (ENTER/EXIT)
- `CallTreeCollector` - Thread-local stacks + bounded event buffer
- `Sampler` - Interface for sampling strategies
- `AdaptiveSampler` - Adjusts sample rate based on overhead

**Key Design Decisions**:
- **ThreadLocal stacks** - No locks, no contention
- **ConcurrentLinkedQueue** - Lock-free event buffer (alternative: LMAX Disruptor)
- **Bounded buffer** - Drop events when full (never OOM)
- **Adaptive sampling** - Feedback loop: measure overhead → adjust sample rate
- **Recursion detection** - Track active frames, detect cycles
- **Max depth limit** - Protect against runaway recursion (512 frames)
- **Cleanup thread-locals** - Prevent leaks in thread pools

### 5. Analysis Layer (`analysis/`)

**Responsibility**: Build call trees, calculate metrics

**Classes**:
- `CallTreeAnalyzer` - Converts events → call tree
- `SelfTimeCalculator` - Computes self-time for each node
- `FlameGraphBuilder` - Converts call tree → folded stacks
- `FlameNode`, `FlameGraph`, `ThreadFlameGraph` - Model classes

**Key Design Decisions**:
- **Per-thread analysis** - Build separate trees per thread, merge if needed
- **Handle unmatched events** - Log errors, continue (resilient to corruption)
- **Self-time calculation** - Post-order traversal (children first)
- **Flame graph derivation** - Depth-first traversal, emit leaf stacks
- **Standard format** - Folded stacks compatible with flamegraph.pl, Speedscope

### 6. Reporting Layer (`reporting/`)

**Responsibility**: Serialize results

**Classes**:
- `Reporter` - Interface (async and sync methods)
- `FlameGraphReporter` - Export folded stacks
- `JsonReporter` - Export JSON
- `FoldedStackExporter` - Write folded stack format

**Key Design Decisions**:
- **Async by default** - Never block event collection
- **Pluggable formats** - Easy to add new reporters
- **Timestamped files** - Avoid overwrites
- **Handle I/O errors** - Log, don't crash

## Critical Design Patterns

### 1. Context Object Pattern

**Problem**: Need to share state across subsystems without static singletons

**Solution**: Single `VTracerContext` created at startup, passed explicitly

```java
public class VTracerContext {
    private final VTracerConfig config;
    private final CallTreeCollector collector;
    private final AdaptiveSampler sampler;
    // ...
    
    public static VTracerContext create(VTracerConfig config) {
        // Wire dependencies
        CallTreeCollector collector = new CallTreeCollector(config.getBufferSize());
        AdaptiveSampler sampler = new AdaptiveSampler(config.getInitialSampleRate());
        // ...
        return new VTracerContext(config, collector, sampler, ...);
    }
}
```

### 2. Bounded Buffer Pattern

**Problem**: Unbounded collections cause OOM in long-running apps

**Solution**: Fixed-size buffer, drop events when full

```java
private final Queue<TraceEvent> eventBuffer = new ConcurrentLinkedQueue<>();
private final int bufferCapacity = 65536;

private void offerEvent(TraceEvent event) {
    if (sampledEvents.get() < bufferCapacity) {
        eventBuffer.offer(event);
        sampledEvents.incrementAndGet();
    } else {
        droppedEvents.incrementAndGet();
    }
}
```

### 3. Adaptive Sampling Pattern

**Problem**: Fixed sampling causes either too much overhead or too little data

**Solution**: Feedback loop that adjusts sample rate

```java
public void adjust(double measuredOverhead) {
    if (measuredOverhead > targetOverhead * 1.5) {
        sampleRate *= 0.7; // Back off
    } else if (measuredOverhead < targetOverhead * 0.5) {
        sampleRate = Math.min(1.0, sampleRate * 1.1); // Ramp up
    }
}
```

### 4. Inline Advice Pattern

**Problem**: ByteBuddy advice creates extra stack frames and allocations

**Solution**: Use `inline = true` for zero-overhead advice

```java
@Advice.OnMethodEnter(inline = true)
public static long enter(...) {
    // Inlined directly into instrumented method
}
```

## Performance Characteristics

### Overhead Model

| Component | Overhead per Call |
|-----------|-------------------|
| Instrumentation (enter/exit) | ~50ns |
| Timestamp collection | ~30ns |
| ThreadLocal lookup | ~5ns |
| Stack push/pop | ~10ns |
| Event buffer offer | ~20ns |
| **Total (when sampled)** | **~115ns** |

With 5% sampling: `115ns × 0.05 = 5.75ns` average overhead per call

At 100k calls/sec per thread × 10 threads:
- Total calls: 1M/sec
- Overhead: 5.75ms/sec = **0.575% CPU**

### Memory Footprint

| Component | Size |
|-----------|------|
| Event buffer (65k events) | ~5MB |
| ThreadLocal stacks (100 threads × 512 depth) | ~2MB |
| Call tree (1M nodes) | ~100MB |
| **Total** | **~107MB** |

### Scalability Limits

- **Threads**: 1000+ (ThreadLocal design)
- **Method calls/sec**: 1M+ (lock-free queues)
- **Call tree depth**: 512 (configurable)
- **Buffer size**: 65k events (configurable, must be power of 2)

## Correctness Guarantees

### 1. Recursion Handling

**Problem**: Recursive calls create cycles in call tree

**Solution**: Track active frames, detect cycles

```java
private final Set<String> activeFrames = new HashSet<>();

public void push(CallFrame frame) {
    String sig = frame.getMethod();
    if (activeFrames.contains(sig)) {
        // Recursive call - mark specially
    }
    frames.push(frame);
    activeFrames.add(sig);
}
```

### 2. Exception Unwinding

**Problem**: Exceptions skip normal method exits, corrupt call stack

**Solution**: `@Advice.OnMethodExit(onThrowable = Throwable.class)`

```java
@Advice.OnMethodExit(onThrowable = Throwable.class)
public static void exit(@Advice.Thrown Throwable thrown) {
    // Called even if exception thrown
    ctx.getCollector().onMethodExit(threadId, timestamp);
}
```

### 3. Concurrent Access

**Problem**: Multiple threads access shared state

**Solution**:
- ThreadLocal for per-thread state (stacks)
- ConcurrentLinkedQueue for shared buffer
- AtomicLong for counters

### 4. Thread-Local Cleanup

**Problem**: ThreadLocal leaks in thread pools

**Solution**: Explicit cleanup on shutdown

```java
public void cleanup() {
    stacks.remove(); // Remove ThreadLocal value
}
```

## Testing Strategy

### Unit Tests
- Call tree construction with recursion
- Call tree construction with exceptions
- Self-time calculation
- Adaptive sampler behavior
- Event buffer overflow handling

### Integration Tests
- 1M method calls across 10 threads
- GC pressure test (ensure no leaks)
- Dynamic attach/detach
- Config hot-reload

### Benchmarks (JMH)
- Instrumentation overhead vs uninstrumented
- Event collection throughput
- Call tree analysis latency
- Memory footprint over 1hr run

## Future Enhancements

### Short Term
1. **CPU time tracking** - Use ThreadMXBean.getThreadCpuTime()
2. **Per-method filtering** - Fine-grained control
3. **Config hot-reload** - Update sampling rate without restart
4. **Metrics export** - Prometheus/StatsD integration

### Long Term
1. **Allocation profiling** - Track object allocations
2. **Lock contention** - Track blocked threads
3. **Web UI** - Live visualization of call trees
4. **eBPF integration** - Native code correlation
5. **Distributed tracing** - Cross-service call graphs

## Comparison to Production Profilers

### vs AsyncProfiler

| Aspect | AsyncProfiler | VTracer |
|--------|---------------|---------|
| Method | JVMTI + perf | ByteBuddy |
| Overhead | <1% | 2-10% |
| Filtering | Limited | **Fine-grained** |
| Native code | Yes | No |
| Allocation | Yes | No |
| Setup | Attach script | -javaagent |

**VTracer's Niche**: Fine-grained filtering for targeted analysis

### vs JFR

| Aspect | JFR | VTracer |
|--------|-----|---------|
| Method | JVMTI events | ByteBuddy |
| Overhead | <1% | 2-10% |
| Portability | OpenJDK only | Any JVM |
| Events | Rich (GC, locks) | Call trees only |
| Setup | -XX:StartFlightRecording | -javaagent |

**VTracer's Niche**: Pure Java, portable, educational

## Conclusion

VTracer is architected to be a **production-grade profiler for targeted analysis**. It will never match AsyncProfiler/JFR on overhead, but it provides:

- **Precise call trees** (not sampled)
- **Fine-grained filtering**
- **Pure Java portability**
- **Educational value** (readable codebase)

The architecture is designed for **correctness, observability, and extensibility**.