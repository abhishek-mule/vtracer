package com.example.vtracer;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import java.lang.instrument.Instrumentation;
import java.util.Random;
import jdk.jfr.consumer.RecordingStream;

public class Agent {

    private static RecordingStream rs;
    private static volatile boolean tracingEnabled = true;
    private static final double SAMPLING_RATE = 0.10; // 10%
    private static final Random RANDOM = new Random();

    public static void premain(String agentArgs, Instrumentation inst) {
        startInstrumentation(inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        startInstrumentation(inst);
    }

    private static void startInstrumentation(Instrumentation inst) {
        System.out.println("[vtracer] Agent loaded – sampling rate: 10%, JFR pinning detection enabled");

        // JFR for pinning detection
        rs = new RecordingStream();
        rs.enable("jdk.VirtualThreadPinned");
        rs.onEvent("jdk.VirtualThreadPinned", event -> {
            if (tracingEnabled) {
                System.out.printf("[vtracer] ⚠️ PINNING DETECTED! Thread: %s, Duration: %d ns%n",
                        event.getThread().getJavaName(),
                        event.getDuration().toNanos());
            }
        });
        rs.startAsync();

        // ByteBuddy instrumentation with sampling
        new AgentBuilder.Default()
                .type(ElementMatchers.nameStartsWith("com.example")) // Limit to your package
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) ->
                        builder.visit(Advice.to(MethodTimer.class)
                                .on(ElementMatchers.isMethod()
                                        .and(ElementMatchers.not(ElementMatchers.isSynthetic())))))
                .installOn(inst);

        System.out.println("[vtracer] Instrumentation active – ready for tracing!");
    }

    public static class MethodTimer {

        @Advice.OnMethodEnter
        public static long enter() {
            if (!tracingEnabled || RANDOM.nextDouble() > SAMPLING_RATE) {
                return -1; // Skip
            }
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(@Advice.Enter long startTime, @Advice.Origin String methodName) {
            if (startTime == -1) return;

            long durationNs = System.nanoTime() - startTime;
            double durationMs = durationNs / 1_000_000.0;

            System.out.printf("[vtracer] [sampled] Method %s executed in %.2f ms%n", methodName, durationMs);

            // Basic circuit breaker – agar method 5 second se zyada le toh disable
            if (durationMs > 5000) {
                tracingEnabled = false;
                System.out.println("[vtracer] CIRCUIT BREAKER OPENED – high latency detected, disabling tracing");
            }
        }
    }
}