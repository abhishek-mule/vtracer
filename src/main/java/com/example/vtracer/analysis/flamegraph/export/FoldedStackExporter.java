package com.example.vtracer.analysis.flamegraph.export;

import com.example.vtracer.analysis.flamegraph.model.FlameGraph;
import com.example.vtracer.util.TimeUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Exports flame graph to folded stack format
 *
 * Format: stacktrace;with;semicolons <weight>
 *
 * This format is consumed by:
 * - flamegraph.pl (Brendan Gregg)
 * - Speedscope
 * - FlameGraph tools
 */
public class FoldedStackExporter {

    /**
     * Export flame graph to folded stack file
     */
    public static void export(FlameGraph flameGraph, Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            for (Map.Entry<String, Long> entry : flameGraph.getFoldedStacks().entrySet()) {
                String stack = entry.getKey();
                long nanos = entry.getValue();

                // Convert nanoseconds to samples (1 sample = 1ms by convention)
                long samples = TimeUtils.nanosToMillis(nanos);

                if (samples > 0) {
                    writer.write(stack);
                    writer.write(" ");
                    writer.write(String.valueOf(samples));
                    writer.write("\n");
                }
            }
        }
    }

    /**
     * Export with custom time unit
     */
    public static void exportWithUnit(FlameGraph flameGraph, Path outputPath, String unit)
            throws IOException {

        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            // Write header comment
            writer.write("# Flame graph data\n");
            writer.write("# Time unit: " + unit + "\n");

            for (Map.Entry<String, Long> entry : flameGraph.getFoldedStacks().entrySet()) {
                String stack = entry.getKey();
                long value = entry.getValue();

                // Convert based on unit
                long displayValue;
                switch (unit.toLowerCase()) {
                    case "ns":
                        displayValue = value;
                        break;
                    case "us":
                        displayValue = value / 1000;
                        break;
                    case "ms":
                        displayValue = value / 1_000_000;
                        break;
                    case "s":
                        displayValue = value / 1_000_000_000;
                        break;
                    default:
                        displayValue = value;
                }

                if (displayValue > 0) {
                    writer.write(stack);
                    writer.write(" ");
                    writer.write(String.valueOf(displayValue));
                    writer.write("\n");
                }
            }
        }
    }
}