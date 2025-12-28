package com.example.vtracer.agent;

import com.example.vtracer.Instrumentation.InstrumentationSetup;
import java.lang.instrument.Instrumentation;

/** Bootstraps the instrumentation setup */
public class AgentBootstrap {

  public static void initialize(VTracerAgent.VTracerContext context, Instrumentation inst) {
    System.out.println("[VTracer] Installing instrumentation...");

    InstrumentationSetup setup = new InstrumentationSetup();
    setup.install(context, inst);

    System.out.println("[VTracer] Instrumentation installed successfully");
  }
}
