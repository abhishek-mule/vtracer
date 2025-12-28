package com.example.vtracer;

import com.example.vtracer.reporting.JsonReporter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.description.type.TypeDescription;
import java.lang.instrument.Instrumentation;
import java.lang.StackTraceElement;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;
import org.yaml.snakeyaml.Yaml;
import java.io.FileInputStream;

public class Agent {

    private static RecordingStream rs;
    private static VTracerConfig config;

    public static void premain(String agentArgs, Instrumentation inst) {
        config = loadConfig();
        startInstrumentation(inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        config = loadConfig();
        startInstrumentation(inst);
    }

    private static VTracerConfig loadConfig() {
        Yaml yaml = new Yaml();
        try (FileInputStream input = new FileInputStream("vtracer-config.yml")) {
            System.out.println("[vtracer] Loaded configuration from vtracer-config.yml");
            return yaml.loadAs(input, VTracerConfig.class);
        } catch (Exception e) {
            System.out.println("[vtracer] Config file not found or invalid, using defaults: " + e.getMessage());
            return new VTracerConfig();
        }
    }

    private static void startInstrumentation(Instrumentation inst) {
        System.out.printf("[vtracer] Agent loaded – sampling rate: %.0f%%, pinning detection: %b%n",
                config.sampling.rate * 100, config.pinningDetection.enabled);

        // JFR pinning detection
        if (config.pinningDetection.enabled) {
            rs = new RecordingStream();
            rs.enable("jdk.VirtualThreadPinned");
            rs.onEvent("jdk.VirtualThreadPinned", event -> {
                if (MethodTimer.tracingEnabled) {
                    long durationNs = event.getDuration().toNanos();
                    RecordedThread recordedThread = event.getThread();
                    String threadName = (recordedThread != null) ? recordedThread.getJavaName() : "unknown-thread";
                    System.out.printf("[vtracer] ⚠️ PINNING DETECTED! Thread: %s, Duration: %d ns%n", threadName, durationNs);
                    JsonReporter.addPinningEvent(threadName, durationNs);
                }
            });
            rs.startAsync();
        }

        // Build type matcher from config
        ElementMatcher.Junction<TypeDescription> typeMatcher = ElementMatchers.none();

        for (String pkg : config.instrumentation.includePackages) {
            typeMatcher = typeMatcher.or(ElementMatchers.nameStartsWith(pkg));
        }

        for (String pkg : config.instrumentation.excludePackages) {
            typeMatcher = typeMatcher.and(ElementMatchers.not(ElementMatchers.nameStartsWith(pkg)));
        }

        new AgentBuilder.Default()
                .type(typeMatcher)
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) ->
                        builder.visit(Advice.to(MethodTimer.class)
                                .on(ElementMatchers.isMethod()
                                        .and(ElementMatchers.not(ElementMatchers.isSynthetic())))))
                .installOn(inst);

        System.out.println("[vtracer] Instrumentation active with configured package filtering!");
    }

    // --- Configuration Classes ---
    public static class VTracerConfig {
        public Sampling sampling = new Sampling();
        public Instrumentation instrumentation = new Instrumentation();
        public PinningDetection pinningDetection = new PinningDetection();
        public Overhead overhead = new Overhead();

        public static class Sampling {
            public double rate = 0.10;
            public boolean enabled = true;
        }

        public static class Instrumentation {
            public List<String> includePackages = List.of("com.example");
            public List<String> excludePackages = List.of("java.", "sun.", "jdk.internal", "net.bytebuddy");
        }

        public static class PinningDetection {
            public boolean enabled = true;
        }

        public static class Overhead {
            public double circuitBreakerThresholdMs = 1000.0;
        }
    }

    // --- Method Timer with Adaptive Sampling ---
    public static class MethodTimer {
        public static volatile boolean tracingEnabled = true;

        // Cache to remember last duration of each method for adaptive sampling
        private static final ConcurrentHashMap<String, Double> methodHistory = new ConcurrentHashMap<>();

        @Advice.OnMethodEnter
        public static long enter(@Advice.Origin String methodName) {
            if (!tracingEnabled || !config.sampling.enabled) return -1;

            // Adaptive sampling: slow methods (last > 100ms) → 100%, fast → 1%
            Double lastDuration = methodHistory.get(methodName);
            double rate = (lastDuration != null && lastDuration > 100.0) ? 1.0 : 0.01;

            if (Math.random() > rate) return -1;
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(@Advice.Enter long startTime, @Advice.Origin String methodName) {
            if (startTime == -1) return;

            long durationNs = System.nanoTime() - startTime;
            double durationMs = durationNs / 1_000_000.0;

            // Stack trace capture kar
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

            System.out.printf("[vtracer] [sampled] Method %s executed in %.2f ms%n", methodName, durationMs);
            JsonReporter.addMethodTrace(methodName, durationMs, stackTrace); // 3 arguments

            if (durationMs > config.overhead.circuitBreakerThresholdMs) {
                tracingEnabled = false;
                System.out.println("[vtracer] 🛑 CIRCUIT BREAKER OPENED – tracing disabled");
            }
        }
    }
}