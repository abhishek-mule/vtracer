package com.example.vtracer;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import java.lang.instrument.Instrumentation;
import java.util.Random;
import jdk.jfr.consumer.RecordingStream;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;

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
        try (InputStream input = new java.io.FileInputStream("vtracer-config.yml")) {
            System.out.println("[vtracer] Loaded config from vtracer-config.yml");
            return yaml.loadAs(input, VTracerConfig.class);
        } catch (Exception e) {
            System.out.println("[vtracer] External config not found, using defaults: " + e.getMessage());
            return new VTracerConfig();
        }
    }

    private static void startInstrumentation(Instrumentation inst) {
        System.out.printf("[vtracer] Agent loaded – sampling: %.0f%%, pinning detection: %b%n",
                config.sampling.rate * 100, config.pinningDetection.enabled);

        // JFR pinning detection
        if (config.pinningDetection.enabled) {
            rs = new RecordingStream();
            rs.enable("jdk.VirtualThreadPinned");
            rs.onEvent("jdk.VirtualThreadPinned", event -> {
                if (MethodTimer.tracingEnabled) {
                    long durationNs = event.getDuration().toNanos();
                    String threadName = event.getThread().getJavaName();
                    System.out.printf("[vtracer] ⚠️ PINNING DETECTED! Thread: %s, Duration: %d ns%n", threadName, durationNs);
                    JsonReporter.addPinningEvent(threadName, durationNs);
                }
            });
            rs.startAsync();
        }

        // Dynamic type matcher from config
        AgentBuilder.TypeStrategy.Default typeStrategy = new AgentBuilder.Default()
                .type(buildTypeMatcher());

        typeStrategy
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) ->
                        builder.visit(Advice.to(MethodTimer.class)
                                .on(ElementMatchers.isMethod()
                                        .and(ElementMatchers.not(ElementMatchers.isSynthetic())))))
                .installOn(inst);

        System.out.println("[vtracer] Instrumentation active with selective filtering!");
    }

    private static ElementMatcher.Junction<ClassLoader> buildTypeMatcher() {
        ElementMatcher.Junction<ClassLoader> matcher = ElementMatchers.none();

        for (String pkg : config.instrumentation.includePackages) {
            matcher = matcher.or(ElementMatchers.nameStartsWith(pkg));
        }

        for (String pkg : config.instrumentation.excludePackages) {
            matcher = matcher.and(ElementMatchers.not(ElementMatchers.nameStartsWith(pkg)));
        }

        return matcher;
    }

    // Config classes
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
            public List<String> excludePackages = List.of("java.", "sun.", "jdk.internal");
        }

        public static class PinningDetection {
            public boolean enabled = true;
        }

        public static class Overhead {
            public double circuitBreakerThresholdMs = 1000.0;
        }
    }

    public static class MethodTimer {
        public static volatile boolean tracingEnabled = true;

        @Advice.OnMethodEnter
        public static long enter() {
            if (!tracingEnabled || !config.sampling.enabled || Math.random() > config.sampling.rate) {
                return -1;
            }
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(@Advice.Enter long startTime, @Advice.Origin String methodName) {
            if (startTime == -1) return;

            long durationNs = System.nanoTime() - startTime;
            double durationMs = durationNs / 1_000_000.0;

            System.out.printf("[vtracer] [sampled] Method %s executed in %.2f ms%n", methodName, durationMs);
            JsonReporter.addMethodTrace(methodName, durationMs);

            if (durationMs > config.overhead.circuitBreakerThresholdMs) {
                tracingEnabled = false;
                System.out.println("[vtracer] 🛑 CIRCUIT BREAKER OPENED – high latency detected");
            }
        }
    }
}