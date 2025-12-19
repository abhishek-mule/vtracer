package com.example.vtracer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JsonReporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<TraceEntry> traces = new ArrayList<>();
    private static final List<StackTraceEntry> stackTraces = new ArrayList<>();

    private static final String TIMESTAMP = Instant.now().toString().replace(":", "-");
    private static final String VTRACER_ROOT = "C:\\Users\\HP\\Desktop\\My java projects\\vtracer";

    private static final String REPORT_FILE = VTRACER_ROOT + "\\vtracer-report-" + TIMESTAMP + ".json";
    private static final String FLAME_FILE = VTRACER_ROOT + "\\vtracer-flame-" + TIMESTAMP + ".txt";

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[vtracer] SHUTDOWN HOOK TRIGGERED – generating reports...");
            writeReport();
            writeFlameGraph();
        }));
    }

    public static synchronized void addMethodTrace(String methodName, double durationMs, StackTraceElement[] stackTrace) {
        traces.add(new TraceEntry("method_timing", methodName, durationMs, Instant.now().toString()));
        stackTraces.add(new StackTraceEntry(methodName, durationMs, stackTrace));
    }

    public static synchronized void addPinningEvent(String threadName, long durationNs) {
        traces.add(new TraceEntry("virtual_thread_pinning", threadName, durationNs / 1_000_000.0, Instant.now().toString()));
    }

    public static synchronized void writeReport() {
        if (traces.isEmpty()) return;
        try (FileWriter writer = new FileWriter(REPORT_FILE)) {
            GSON.toJson(traces, writer);
            System.out.println("[vtracer] JSON report written to: " + REPORT_FILE);
        } catch (IOException e) {
            System.out.println("[vtracer] Failed to write report: " + e.getMessage());
        }
    }

    /**
     * Generates a Folded Stack file compatible with Speedscope.app
     */
    public static synchronized void writeFlameGraph() {
        if (stackTraces.isEmpty()) {
            System.out.println("[vtracer] No stack traces – skipping flame graph");
            return;
        }

        try (FileWriter writer = new FileWriter(FLAME_FILE)) {
            for (StackTraceEntry entry : stackTraces) {
                StringBuilder foldedLine = new StringBuilder();

                // Bottom to top: Reconstructing the call hierarchy
                for (int i = entry.stackTrace.length - 1; i >= 0; i--) {
                    String frame = entry.stackTrace[i];

                    // Noise filter: Removes profiling overhead from the visual report
                    if (frame.contains("java.lang.Thread") ||
                            frame.contains("com.example.vtracer") ||
                            frame.contains("net.bytebuddy")) {
                        continue;
                    }

                    foldedLine.append(frame.replace(" ", ""));
                    if (i > 0) foldedLine.append(";");
                }

                if (foldedLine.length() > 0) {
                    String line = foldedLine.toString();
                    if (line.endsWith(";")) line = line.substring(0, line.length() - 1);

                    // Duration in microseconds (us) for high-precision Flame Graphs
                    long durationUs = (long) (entry.durationMs * 1000);
                    writer.write(line + " " + durationUs + "\n");
                }
            }
            System.out.println("[vtracer] 🔥 Flame graph folded file written to " + FLAME_FILE);
        } catch (IOException e) {
            System.out.println("[vtracer] Failed to write flame graph: " + e.getMessage());
        }
    }

    private static class TraceEntry {
        String type, name, timestamp;
        double durationMs;
        TraceEntry(String type, String name, double durationMs, String timestamp) {
            this.type = type; this.name = name; this.durationMs = durationMs; this.timestamp = timestamp;
        }
    }

    private static class StackTraceEntry {
        double durationMs;
        String[] stackTrace;
        StackTraceEntry(String methodName, double durationMs, StackTraceElement[] stackTrace) {
            this.durationMs = durationMs;
            this.stackTrace = Arrays.stream(stackTrace)
                    .map(StackTraceElement::toString)
                    .toArray(String[]::new);
        }
    }
}