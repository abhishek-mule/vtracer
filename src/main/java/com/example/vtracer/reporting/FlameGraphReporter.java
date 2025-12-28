package com.example.vtracer.reporting;

import com.example.vtracer.analysis.calltree.CallTreeAnalyzer.CallTree;
import com.example.vtracer.analysis.flamegraph.builder.FlameGraphBuilder;
import com.example.vtracer.analysis.flamegraph.export.FoldedStackExporter;
import com.example.vtracer.analysis.flamegraph.model.FlameGraph;
import com.example.vtracer.analysis.flamegraph.model.ThreadFlameGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Flame graph reporter
 *
 * Exports call tree as folded stack format for flame graph visualization
 */
public class FlameGraphReporter implements Reporter {

    private final Path outputDir;
    private final boolean perThread;

    public FlameGraphReporter(Path outputDir) {
        this(outputDir, false);
    }

    public FlameGraphReporter(Path outputDir, boolean perThread) {
        this.outputDir = outputDir;
        this.perThread = perThread;
    }

    @Override
    public CompletableFuture<Void> reportAsync(CallTree tree) {
        return CompletableFuture.runAsync(() -> reportSync(tree));
    }

    @Override
    public void reportSync(CallTree tree) {
        try {
            Files.createDirectories(outputDir);

            String timestamp = Instant.now().toString().replace(":", "-");

            if (perThread) {
                // Generate per-thread flame graphs
                ThreadFlameGraph threadFg = FlameGraphBuilder.buildPerThread(tree);

                for (Map.Entry<Long, FlameGraph> entry : threadFg.getFlameGraphsByThread().entrySet()) {
                    long threadId = entry.getKey();
                    FlameGraph fg = entry.getValue();

                    Path outputPath = outputDir.resolve(
                            "flamegraph-thread-" + threadId + "-" + timestamp + ".folded"
                    );

                    FoldedStackExporter.export(fg, outputPath);
                    System.out.println("[VTracer] Flame graph (thread " + threadId + ") written to: " + outputPath);
                }

                // Also generate merged
                FlameGraph merged = threadFg.merge();
                Path mergedPath = outputDir.resolve("flamegraph-merged-" + timestamp + ".folded");
                FoldedStackExporter.export(merged, mergedPath);
                System.out.println("[VTracer] Merged flame graph written to: " + mergedPath);

            } else {
                // Generate single merged flame graph
                FlameGraph flameGraph = FlameGraphBuilder.build(tree);
                Path outputPath = outputDir.resolve("flamegraph-" + timestamp + ".folded");

                FoldedStackExporter.export(flameGraph, outputPath);
                System.out.println("[VTracer] Flame graph written to: " + outputPath);
                System.out.println("[VTracer] Generate SVG with: flamegraph.pl " + outputPath + " > flamegraph.svg");
            }

        } catch (IOException e) {
            System.err.println("[VTracer] Failed to write flame graph: " + e.getMessage());
        }
    }
}