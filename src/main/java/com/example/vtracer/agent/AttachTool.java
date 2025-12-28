package com.example.vtracer.agent;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.util.List;

/**
 * Dynamic attach tool for attaching VTracer to running JVMs
 *
 * <p>Usage: java -cp vtracer.jar com.example.vtracer.agent.AttachTool <pid> [options]
 */
public class AttachTool {

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println(
          "Usage: java -cp vtracer.jar com.example.vtracer.agent.AttachTool <pid|list> [options]");
      System.out.println("  list  - List all running JVMs");
      System.out.println("  <pid> - Attach to specific JVM process");
      System.exit(1);
    }

    String command = args[0];

    if ("list".equals(command)) {
      listJVMs();
    } else {
      String pid = command;
      String agentArgs = args.length > 1 ? args[1] : "";
      attachToJVM(pid, agentArgs);
    }
  }

  private static void listJVMs() {
    System.out.println("Available JVMs:");
    List<VirtualMachineDescriptor> vms = VirtualMachine.list();

    if (vms.isEmpty()) {
      System.out.println("  No JVMs found");
      return;
    }

    for (VirtualMachineDescriptor vmd : vms) {
      System.out.printf("  PID: %s, Name: %s%n", vmd.id(), vmd.displayName());
    }
  }

  private static void attachToJVM(String pid, String agentArgs) {
    try {
      System.out.println("Attaching to JVM with PID: " + pid);

      VirtualMachine vm = VirtualMachine.attach(pid);

      // Get agent JAR path (assume it's in same location as this class)
      String agentPath =
          AttachTool.class.getProtectionDomain().getCodeSource().getLocation().getPath();

      System.out.println("Loading agent: " + agentPath);
      System.out.println("Agent args: " + agentArgs);

      vm.loadAgent(agentPath, agentArgs);
      vm.detach();

      System.out.println("Agent attached successfully");

    } catch (Exception e) {
      System.err.println("Failed to attach: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}
