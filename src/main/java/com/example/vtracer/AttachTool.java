package com.example.vtracer;

import com.sun.tools.attach.VirtualMachine;

public class AttachTool {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java AttachTool <PID>");
            return;
        }

        String pid = args[0];
        String agentJar = "C:\\Users\\HP\\Desktop\\My java projects\\vtracer\\target\\vtracer-1.0.jar";  // tera exact path daal (double backslash)

        System.out.println("[vtracer] Attaching to PID: " + pid);

        VirtualMachine vm = VirtualMachine.attach(pid);
        vm.loadAgent(agentJar);
        System.out.println("[vtracer] Agent successfully attached to PID " + pid);
        vm.detach();
    }
}