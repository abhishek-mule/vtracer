package com.example.vtracer.reporting;

import com.example.vtracer.analysis.calltree.CallTreeAnalyzer.CallTree;
import com.example.vtracer.util.TimeUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * JSON reporter for call trees
 *
 * Exports call tree in JSON format for custom analysis
 */
public class JsonReporter implements Reporter {

    private final Path outputDir;

    public JsonReporter(Path outputDir) {
        this.outputDir = outputDir;
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
            Path outputPath = outputDir.resolve("calltree-" + timestamp + ".json");

            try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
                writeJson(writer, tree);
            }

            System.out.println("[VTracer] JSON report written to: " + outputPath);

        } catch (IOException e) {
            System.err.println("[VTracer] Failed to write JSON report: " + e.getMessage());
        }
    }

    private void writeJson(BufferedWriter writer, CallTree tree) throws IOException {
        writer.write("{\n");
        writer.write("  \"roots\": [\n");

        boolean first = true;
        for (CallTree.Node root : tree.getRoots()) {
            if (!first) {
                writer.write(",\n");
            }
            writeNode(writer, root, 2);
            first = false;
        }

        writer.write("\n  ]\n");
        writer.write("}\n");
    }

    private void writeNode(BufferedWriter writer, CallTree.Node node, int indent)
            throws IOException {

        String indentStr = " ".repeat(indent);

        writer.write(indentStr + "{\n");
        writer.write(indentStr + "  \"method\": \"" + escapeJson(node.getMethodSignature()) + "\",\n");
        writer.write(indentStr + "  \"className\": \"" + escapeJson(node.getClassName()) + "\",\n");
        writer.write(indentStr + "  \"methodName\": \"" + escapeJson(node.getMethodName()) + "\",\n");
        writer.write(indentStr + "  \"threadId\": " + node.getThreadId() + ",\n");
        writer.write(indentStr + "  \"startTime\": " + node.getStartTime() + ",\n");
        writer.write(indentStr + "  \"endTime\": " + node.getEndTime() + ",\n");
        writer.write(indentStr + "  \"totalTimeNs\": " + node.getTotalTime() + ",\n");
        writer.write(indentStr + "  \"selfTimeNs\": " + node.getSelfTime() + ",\n");
        writer.write(indentStr + "  \"totalTimeMs\": " + TimeUtils.nanosToMillis(node.getTotalTime()) + ",\n");
        writer.write(indentStr + "  \"selfTimeMs\": " + TimeUtils.nanosToMillis(node.getSelfTime()) + ",\n");
        writer.write(indentStr + "  \"children\": [\n");

        boolean first = true;
        for (CallTree.Node child : node.getChildren()) {
            if (!first) {
                writer.write(",\n");
            }
            writeNode(writer, child, indent + 2);
            first = false;
        }

        writer.write("\n" + indentStr + "  ]\n");
        writer.write(indentStr + "}");
    }

    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}