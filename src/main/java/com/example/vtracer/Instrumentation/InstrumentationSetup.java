package com.example.vtracer.instrumentation;

import com.example.vtracer.agent.VTracerAgent;
import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

/** Sets up ByteBuddy instrumentation */
public class InstrumentationSetup {

  public void install(VTracerAgent.VTracerContext context, Instrumentation inst) {
    AgentBuilder agentBuilder =
        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
            .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
            .ignore(context.getConfig().getClassMatcher().negate())
            .type(context.getConfig().getClassMatcher())
            .transform(
                (builder, typeDescription, classLoader, module, protectionDomain) ->
                    builder.visit(
                        Advice.to(MethodAdvice.class).on(context.getConfig().getMethodMatcher())))
            .with(
                new AgentBuilder.Listener.Adapter() {
                  @Override
                  public void onError(
                      String typeName,
                      ClassLoader classLoader,
                      net.bytebuddy.utility.JavaModule module,
                      boolean loaded,
                      Throwable throwable) {
                    System.err.println(
                        "[VTracer] Error instrumenting "
                            + typeName
                            + ": "
                            + throwable.getMessage());
                  }

                  @Override
                  public void onComplete(
                      String typeName,
                      ClassLoader classLoader,
                      net.bytebuddy.utility.JavaModule module,
                      boolean loaded) {
                    // Verbose logging disabled by default
                    // System.out.println("[VTracer] Instrumented: " + typeName);
                  }
                });

    agentBuilder.installOn(inst);
  }
}
