package com.example.vtracer;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import java.lang.instrument.Instrumentation;
import java.util.Random;
import jdk.jfr.consumer.RecordingStream;

public class Agent {

    private static RecordingStream rs;

    public static void premain(String agentArgs, Instrumentation inst) {
        startInstrumentation(inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        startInstrumentation(inst);
    }

    private static void startInstrumentation(Instrumentation inst) {
        System.out.println("[vtracer] Agent loaded – sampling rate: 10%, JFR pinning detection enabled");

        // Shutdown hook for final report
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[vtracer] JVM shutting down – generating final report...");
            JsonReporter.writeReport();
        }));

        // JFR for pinning detection
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

        // ByteBuddy instrumentation
        new AgentBuilder.Default()
                .type(ElementMatchers.nameStartsWith("com.example"))
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) ->
                        builder.visit(Advice.to(MethodTimer.class)
                                .on(ElementMatchers.isMethod()
                                        .and(ElementMatchers.not(ElementMatchers.isSynthetic())))))
                .installOn(inst);

        System.out.println("[vtracer] Instrumentation + JFR active – ready for tracing!");
    }

    public static class MethodTimer {
        public static volatile boolean tracingEnabled = true;
        private static final double SAMPLING_RATE = 0.10;
        private static final Random RANDOM = new Random();
        private static final double CIRCUIT_BREAKER_THRESHOLD_MS = 5000.0; // 5 seconds

        @Advice.OnMethodEnter
        public static long enter() {
            if (!tracingEnabled || RANDOM.nextDouble() > SAMPLING_RATE) {
                return -1;
            }
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(@Advice.Enter long startTime, @Advice.Origin String methodName) {
            if (startTime == -1) return;

            long durationNs = System.nanoTime() - startTime;
            double durationMs = durationNs / 1_000_000.0;

            System.out.printf("[vtracer] [sampled] Method %s