package com.example.vtracer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class JsonReporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<TraceEntry> traces = new ArrayList<>();
    private static final String REPORT_FILE = "vtracer-report-" + Instant.now().toString().replace(":", "-") + ".json";

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[vtracer] SHUTDOWN HOOK TRIGGERED – generating JSON report...");
            writeReport();
        }));
    }

    public static synchronized void addMethodTrace(String methodName, double durationMs) {
        traces.add(new TraceEntry("method_timing", methodName, durationMs, Instant.now().toString()));
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
}