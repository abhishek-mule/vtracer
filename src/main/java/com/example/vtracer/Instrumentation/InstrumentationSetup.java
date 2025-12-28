package com.example.vtracer.Instrumentation;

import com.example.vtracer.agent.VTracerAgent;
import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import static net.bytebuddy.matcher.ElementMatchers.*;

/** Sets up ByteBuddy instrumentation */
public class InstrumentationSetup {

  public void install(VTracerAgent.VTracerContext context, Instrumentation inst) {
    AgentBuilder agentBuilder =
        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
            .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
            .ignore(ElementMatchers.nameStartsWith("com.example.vtracer"))
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
                    // Instrumentation completion logging can be added here if needed
                  }
                });

    agentBuilder.installOn(inst);
  }
}
