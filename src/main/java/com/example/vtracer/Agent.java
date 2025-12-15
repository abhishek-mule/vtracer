package com.example.vtracer;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import java.lang.instrument.Instrumentation;
import java.time.Duration;

public class Agent {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[vtracer] Agent loaded – starting ByteBuddy instrumentation");

        new AgentBuilder.Default()
                .type(ElementMatchers.any())  // Test ke liye sab classes
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) ->
                        builder.visit(Advice.to(MethodTimer.class)
                                .on(ElementMatchers.isMethod()
                                        .and(ElementMatchers.not(ElementMatchers.isSynthetic())))))
                .installOn(inst);

        System.out.println("[vtracer] Instrumentation complete – method timing active!");
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