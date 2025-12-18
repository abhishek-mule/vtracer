package com.example.vtracer;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.description.type.TypeDescription;
import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import jdk.jfr.consumer.RecordingStream;
import org.yaml.snakeyaml.Yaml;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.StackTraceElement; // Added import

public class Agent {

    private static RecordingStream rs;
    public static VTracerConfig config;

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
        try (InputStream input = new FileInputStream("vtracer-config.yml")) {
            System.out.println("[vtracer] Loaded configuration from vtracer-config.yml");
            return yaml.loadAs(input, VTracerConfig.class);
        } catch (Exception e) {
            System.out.println("[vtracer] Config file not found or invalid, using defaults");
            return new VTracerConfig();
        }
    }

    private static void startInstrumentation(Instrumentation inst) {
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
    }

    // --- Configuration Classes ---
    public static class VTracerConfig {
        public Sampling sampling = new Sampling();
        public InstrumentationConfig instrumentation = new InstrumentationConfig();
        public PinningDetection pinningDetection = new PinningDetection();
        public Overhead overhead = new Overhead();

        public static class Sampling {
            public double rate = 0.10;
            public boolean enabled = true;
        }
        public static class InstrumentationConfig {
            public List<String> includePackages = List.of("com.example");
            public List<String> excludePackages = List.of("java.", "sun.", "jdk.internal");
        }
        public static class PinningDetection { public boolean enabled = true; }
        public static class Overhead { public double circuitBreakerThresholdMs = 1000.0; }
    }

    // --- Updated Advice Class ---
    public static class MethodTimer {
        public static volatile boolean tracingEnabled = true;

        // Internal cache to make sampling truly adaptive based on history
        private static final ConcurrentHashMap<String, Double> history = new ConcurrentHashMap<>();

        @Advice.OnMethodEnter
        public static long enter(@Advice.Origin String methodName) {
            if (!tracingEnabled || !Agent.config.sampling.enabled) return -1;

            // Adaptive sampling logic
            double durationThresholdMs = 100.0;
            double slowSamplingRate = 1.0;
            double fastSamplingRate = 0.01;

            // Use history if available, otherwise use your random estimate logic
            double lastDuration = history.getOrDefault(methodName, Math.random() * 2000);
            double currentRate = (lastDuration > durationThresholdMs) ? slowSamplingRate : fastSamplingRate;

            if (Math.random() > currentRate) return -1;
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(@Advice.Enter long startTime, @Advice.Origin String methodName) {
            if (startTime == -1) return;

            long durationNs = System.nanoTime() - startTime;
            double durationMs = durationNs / 1_000_000.0;

            // Update history for adaptive sampling
            history.put(methodName, durationMs);

            System.out.printf("[vtracer] [sampled] Method %s executed in %.2f ms%n", methodName, durationMs);

            // Capture stack trace for flame graph
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            JsonReporter.addMethodTrace(methodName, durationMs, stackTrace);

            // Circuit Breaker Check
            if (durationMs > Agent.config.overhead.circuitBreakerThresholdMs) {
                tracingEnabled = false;
                System.out.println("[vtracer] 🛑 CIRCUIT BREAKER OPENED – tracing disabled");
            }
        }
    }
}