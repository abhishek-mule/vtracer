package com.example.vtracer.Instrumentation;

import com.example.vtracer.agent.VTracerAgent;
import net.bytebuddy.asm.Advice;

/**
 * ByteBuddy advice for method entry/exit
 *
 * <p>CRITICAL: inline = true for zero allocation CRITICAL: suppress = Throwable to handle
 * exceptions
 */
public class MethodAdvice {

  /**
   * Called on method entry
   *
   * @return timestamp for passing to exit advice
   */
  @Advice.OnMethodEnter(inline = true)
  public static long enter(
      @Advice.Origin("#t") String className, @Advice.Origin("#m") String methodName) {
    try {
      VTracerAgent.VTracerContext ctx = VTracerAgent.getContext();

      // Fast path: check if running
      if (ctx == null || !ctx.isRunning()) {
        return 0L;
      }

      long threadId = Thread.currentThread().getId();
      long timestamp = System.nanoTime();

      ctx.getCollector().onMethodEnter(threadId, className, methodName, timestamp);

      return timestamp;

    } catch (Throwable t) {
      // Never throw from instrumentation
      return 0L;
    }
  }

  /** Called on method exit (normal or exceptional) */
  @Advice.OnMethodExit(inline = true, onThrowable = Throwable.class)
  public static void exit(@Advice.Enter long startTime, @Advice.Thrown Throwable thrown) {
    try {
      // Early exit if not traced
      if (startTime == 0L) {
        return;
      }

      VTracerAgent.VTracerContext ctx = VTracerAgent.getContext();
      if (ctx == null || !ctx.isRunning()) {
        return;
      }

      long threadId = Thread.currentThread().getId();
      long timestamp = System.nanoTime();

      ctx.getCollector().onMethodExit(threadId, timestamp);

    } catch (Throwable t) {
      // Never throw from instrumentation
    }
  }
}
