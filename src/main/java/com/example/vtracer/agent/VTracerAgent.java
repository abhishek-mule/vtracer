package com.example.vtracer.agent;

import com.example.vtracer.analysis.calltree.CallTreeAnalyzer;
import com.example.vtracer.analysis.calltree.SelfTimeCalculator;
import com.example.vtracer.config.VTracerConfig;
import com.example.vtracer.reporting.FlameGraphReporter;
import com.example.vtracer.reporting.JsonReporter;
import com.example.vtracer.reporting.Reporter;
import com.example.vtracer.tracing.collector.CallTreeCollector;
import com.example.vtracer.tracing.sampling.AdaptiveSampler;
import java.lang.instrument.Instrumentation;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VTracer Java Agent Entry Point
 *
 * <p>This is the ONLY class with static state - holds the singleton context. All other state flows
 * through VTracerContext.
 */
public class VTracerAgent {

  private static volatile VTracerContext context;

  /** Premain entry point - agent loaded at JVM startup */
  public static void premain(String agentArgs, Instrumentation inst) {
    System.out.println("[VTracer] Starting agent (premain)");
    initialize(agentArgs, inst);
  }

  /** Agentmain entry point - dynamic attach */
  public static void agentmain(String agentArgs, Instrumentation inst) {
    System.out.println("[VTracer] Starting agent (agentmain - dynamic attach)");
    initialize(agentArgs, inst);
  }

  private static synchronized void initialize(String agentArgs, Instrumentation inst) {
    if (context != null) {
      System.err.println("[VTracer] Already initialized, skipping");
      return;
    }

    try {
      VTracerConfig config = VTracerConfig.parse(agentArgs);

      if (!config.isEnabled()) {
        System.out.println("[VTracer] Disabled via config, exiting");
        return;
      }

      context = VTracerContext.create(config);

      AgentBootstrap.initialize(context, inst);

      // Register shutdown hook
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    System.out.println("[VTracer] Shutdown initiated");
                    if (context != null) {
                      context.shutdown(30, TimeUnit.SECONDS);
                    }
                  },
                  "vtracer-shutdown"));

      System.out.println("[VTracer] Initialized successfully");

    } catch (Exception e) {
      System.err.println("[VTracer] Failed to initialize: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /** Get the singleton context - called from instrumentation advice */
  public static VTracerContext getContext() {
    return context;
  }

  /**
   * VTracerContext - The Spine of VTracer
   *
   * <p>This object coordinates all subsystems and manages lifecycle. It's the ONLY stateful object
   * shared across all instrumented code.
   */
  public static class VTracerContext {

    private final VTracerConfig config;
    private final CallTreeCollector collector;
    private final AdaptiveSampler sampler;
    private final CallTreeAnalyzer analyzer;
    private final SelfTimeCalculator selfTimeCalculator;
    private final Reporter reporter;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;

    private VTracerContext(
        VTracerConfig config,
        CallTreeCollector collector,
        AdaptiveSampler sampler,
        CallTreeAnalyzer analyzer,
        SelfTimeCalculator selfTimeCalculator,
        Reporter reporter,
        ScheduledExecutorService scheduler) {
      this.config = config;
      this.collector = collector;
      this.sampler = sampler;
      this.analyzer = analyzer;
      this.selfTimeCalculator = selfTimeCalculator;
      this.reporter = reporter;
      this.scheduler = scheduler;
      this.running = new AtomicBoolean(true);
    }

    public static VTracerContext create(VTracerConfig config) {
      CallTreeCollector collector = new CallTreeCollector(config.getBufferSize());
      AdaptiveSampler sampler =
          new AdaptiveSampler(config.getInitialSampleRate(), config.getTargetOverhead());
      CallTreeAnalyzer analyzer = new CallTreeAnalyzer();
      SelfTimeCalculator selfTimeCalculator = new SelfTimeCalculator();

      Reporter reporter;
      switch (config.getOutputFormat()) {
        case FOLDED:
          reporter = new FlameGraphReporter(config.getOutputDir());
          break;
        case JSON:
          reporter = new JsonReporter(config.getOutputDir());
          break;
        default:
          reporter = new FlameGraphReporter(config.getOutputDir());
      }

      ScheduledExecutorService scheduler =
          Executors.newScheduledThreadPool(
              2,
              r -> {
                Thread t = new Thread(r, "vtracer-background");
                t.setDaemon(true);
                return t;
              });

      VTracerContext ctx =
          new VTracerContext(
              config, collector, sampler, analyzer, selfTimeCalculator, reporter, scheduler);

      ctx.startBackgroundJobs();

      return ctx;
    }

    private void startBackgroundJobs() {
      // Periodic flush: drain events, build tree, report
      scheduler.scheduleAtFixedRate(
          () -> {
            try {
              var events = collector.drain();
              if (!events.isEmpty()) {
                var tree = analyzer.buildTree(events);
                selfTimeCalculator.calculate(tree);
                reporter.reportAsync(tree);
              }
            } catch (Exception e) {
              System.err.println("[VTracer] Error in flush job: " + e.getMessage());
            }
          },
          config.getReportIntervalSeconds(),
          config.getReportIntervalSeconds(),
          TimeUnit.SECONDS);

      // Adaptive sampling adjustment
      scheduler.scheduleAtFixedRate(
          () -> {
            try {
              double overhead = collector.getOverheadPercent();
              sampler.adjust(overhead);
            } catch (Exception e) {
              System.err.println("[VTracer] Error in sampling adjustment: " + e.getMessage());
            }
          },
          5,
          5,
          TimeUnit.SECONDS);

      // Metrics export
      scheduler.scheduleAtFixedRate(
          () -> {
            try {
              collector.exportMetrics(config.getOutputDir().resolve("metrics.txt"));
            } catch (Exception e) {
              System.err.println("[VTracer] Error exporting metrics: " + e.getMessage());
            }
          },
          10,
          10,
          TimeUnit.SECONDS);
    }

    public void shutdown(long timeout, TimeUnit unit) {
      System.out.println("[VTracer] Shutting down...");
      running.set(false);

      // Stop background jobs
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(timeout, unit)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }

      // Final flush
      try {
        var events = collector.drain();
        if (!events.isEmpty()) {
          var tree = analyzer.buildTree(events);
          selfTimeCalculator.calculate(tree);
          reporter.reportSync(tree);
        }
      } catch (Exception e) {
        System.err.println("[VTracer] Error in final flush: " + e.getMessage());
      }

      // Cleanup thread-locals
      collector.cleanup();

      System.out.println("[VTracer] Shutdown complete");
    }

    // Getters
    public VTracerConfig getConfig() {
      return config;
    }

    public CallTreeCollector getCollector() {
      return collector;
    }

    public AdaptiveSampler getSampler() {
      return sampler;
    }

    public boolean isRunning() {
      return running.get();
    }
  }
}
