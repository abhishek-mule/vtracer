package com.example.vtracer;

import com.sun.tools.attach.VirtualMachine;
import java.io.File;

public class AttachTool {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java AttachTool <PID>");
            return;
        }

        String pid = args[0];
        String agentJar = new File("target/vtracer-1.0.jar").getAbsolutePath();  // shaded JAR ka absolute path

        System.out.println("[vtracer] Attaching to PID: " + pid);
        System.out.println("[vtracer] Using agent JAR: " + agentJar);

        VirtualMachine vm = VirtualMachine.attach(pid);
        vm.loadAgent(agentJar);
        System.out.println("[vtracer] Agent successfully attached to PID " + pid);
        vm.detach();
    }
}