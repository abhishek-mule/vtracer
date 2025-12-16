package com.example.vtracer;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import java.lang.instrument.Instrumentation;
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
        System.out.println("[vtracer] Agent loaded – starting ByteBuddy + JFR pinning detection");

        // JFR stream start kar pinning events ke liye
        rs = new RecordingStream();
        rs.enable("jdk.VirtualThreadPinned");
        rs.onEvent("jdk.VirtualThreadPinned", event -> {
            System.out.printf("[vtracer] ⚠️  VIRTUAL THREAD PINNING DETECTED! Thread: %s, Duration: %d ns%n",
                    event.getThread().getJavaName(),
                    event.getDuration().toNanos());
        });
        rs.startAsync();

        // ByteBuddy instrumentation (same as Day 2)
        new AgentBuilder.Default()
                .type(ElementMatchers.any())
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) ->
                        builder.visit(Advice.to(MethodTimer.class)
                                .on(ElementMatchers.isMethod()
                                        .and(ElementMatchers.not(ElementMatchers.isSynthetic())))))
                .installOn(inst);

        System.out.println("[vtracer] Instrumentation + JFR pinning detection active!");
    }

    public static class MethodTimer {
        @Advice.OnMethodEnter
        public static long enter() {
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(@Advice.Enter long startTime, @Advice.Origin String methodName) {
            long durationNs = System.nanoTime() - startTime;
            double durationMs = durationNs / 1_000_000.0;
            System.out.printf("[vtracer] Method %s executed in %.2f ms%n", methodName, durationMs);
        }
    }
}