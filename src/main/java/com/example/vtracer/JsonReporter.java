package com.example.vtracer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays; // Added for stream support
import java.util.List;

public class JsonReporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<TraceEntry> traces = new ArrayList<>();
    private static final List<StackTraceEntry> stackTraces = new ArrayList<>(); // New for flame graph
    private static final String REPORT_FILE = "vtracer-report-" + Instant.now().toString().replace(":", "-") + ".json";
    private static final String FLAME_FILE = "vtracer-flame-" + Instant.now().toString().replace(":", "-") + ".json";

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[vtracer] SHUTDOWN HOOK TRIGGERED – generating reports...");
            writeReport();
            writeFlameGraph();
        }));
    }

    // Updated to accept StackTraceElement[] for flame graph generation
    public static synchronized void addMethodTrace(String methodName, double durationMs, StackTraceElement[] stackTrace) {
        traces.add(new TraceEntry("method_timing", methodName, durationMs, Instant.now().toString()));
        stackTraces.add(new StackTraceEntry(methodName, durationMs, stackTrace));
    }

    public static synchronized void addPinningEvent(String threadName, long durationNs) {
        traces.add(new TraceEntry("virtual_thread_pinning", threadName, durationNs / 1_000_000.0, Instant.now().toString()));
    }

    public static synchronized void writeReport() {
        if (traces.isEmpty()) {
            System.out.println("[vtracer] No traces collected – skipping report");
            return;
        }

        try (FileWriter writer = new FileWriter(REPORT_FILE)) {
            GSON.toJson(traces, writer);
            System.out.println("[vtracer] JSON report written to " + REPORT_FILE + " (" + traces.size() + " entries)");
        } catch (IOException e) {
            System.out.println("[vtracer] Failed to write report: " + e.getMessage());
        }
    }

    public static synchronized void writeFlameGraph() {
        if (stackTraces.isEmpty()) {
            System.out.println("[vtracer] No stack traces collected – skipping flame graph");
            return;
        }

        try (FileWriter writer = new FileWriter(FLAME_FILE)) {
            GSON.toJson(stackTraces, writer);
            System.out.println("[vtracer] Flame graph data written to " + FLAME_FILE);
        } catch (IOException e) {
            System.out.println("[vtracer] Failed to write flame graph: " + e.getMessage());
        }
    }

    private static class TraceEntry {
        String type;
        String name;
        double durationMs;
        String timestamp;

        TraceEntry(String type, String name, double durationMs, String timestamp) {
            this.type = type;
            this.name = name;
            this.durationMs = durationMs;
            this.timestamp = timestamp;
        }
    }

    private static class StackTraceEntry {
        String methodName;
        double durationMs;
        String[] stackTrace;

        StackTraceEntry(String methodName, double durationMs, StackTraceElement[] stackTrace) {
            this.methodName = methodName;
            this.durationMs = durationMs;
            // Converting StackTraceElement array to String array for JSON serialization
            this.stackTrace = Arrays.stream(stackTrace)
                    .map(StackTraceElement::toString)
                    .toArray(String[]::new);
        }
    }
}