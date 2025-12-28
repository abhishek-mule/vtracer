package com.example.vtracer.config;

import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * VTracer Configuration
 *
 * Parsed from agent arguments, environment variables, or config files
 */
public class VTracerConfig {

    public enum OutputFormat {
        FOLDED,
        JSON
    }

    private final boolean enabled;
    private final double initialSampleRate;
    private final double targetOverhead;
    private final int bufferSize;
    private final Pattern classIncludePattern;
    private final Pattern classExcludePattern;
    private final Pattern methodIncludePattern;
    private final int maxStackDepth;
    private final Path outputDir;
    private final OutputFormat outputFormat;
    private final int reportIntervalSeconds;

    private VTracerConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.initialSampleRate = builder.initialSampleRate;
        this.targetOverhead = builder.targetOverhead;
        this.bufferSize = builder.bufferSize;
        this.classIncludePattern = builder.classIncludePattern;
        this.classExcludePattern = builder.classExcludePattern;
        this.methodIncludePattern = builder.methodIncludePattern;
        this.maxStackDepth = builder.maxStackDepth;
        this.outputDir = builder.outputDir;
        this.outputFormat = builder.outputFormat;
        this.reportIntervalSeconds = builder.reportIntervalSeconds;
    }

    /**
     * Parse agent arguments
     * Format: "key1=value1,key2=value2"
     */
    public static VTracerConfig parse(String agentArgs) {
        Builder builder = builder();

        // Parse agent args
        if (agentArgs != null && !agentArgs.isEmpty()) {
            Map<String, String> args = parseArgs(agentArgs);

            if (args.containsKey("enabled")) {
                builder.enabled(Boolean.parseBoolean(args.get("enabled")));
            }
            if (args.containsKey("sampleRate")) {
                builder.initialSampleRate(Double.parseDouble(args.get("sampleRate")));
            }
            if (args.containsKey("targetOverhead")) {
                builder.targetOverhead(Double.parseDouble(args.get("targetOverhead")));
            }
            if (args.containsKey("bufferSize")) {
                builder.bufferSize(Integer.parseInt(args.get("bufferSize")));
            }
            if (args.containsKey("classInclude")) {
                builder.classInclude(args.get("classInclude"));
            }
            if (args.containsKey("classExclude")) {
                builder.classExclude(args.get("classExclude"));
            }
            if (args.containsKey("output")) {
                builder.outputDir(Paths.get(args.get("output")));
            }
            if (args.containsKey("format")) {
                builder.outputFormat(OutputFormat.valueOf(args.get("format").toUpperCase()));
            }
            if (args.containsKey("reportInterval")) {
                builder.reportIntervalSeconds(Integer.parseInt(args.get("reportInterval")));
            }
        }

        // Override with environment variables
        String envEnabled = System.getenv("VTRACER_ENABLED");
        if (envEnabled != null) {
            builder.enabled(Boolean.parseBoolean(envEnabled));
        }

        String envSampleRate = System.getenv("VTRACER_SAMPLE_RATE");
        if (envSampleRate != null) {
            builder.initialSampleRate(Double.parseDouble(envSampleRate));
        }

        String envOutput = System.getenv("VTRACER_OUTPUT");
        if (envOutput != null) {
            builder.outputDir(Paths.get(envOutput));
        }

        return builder.build();
    }

    private static Map<String, String> parseArgs(String args) {
        Map<String, String> result = new HashMap<>();
        String[] pairs = args.split(",");

        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0].trim(), kv[1].trim());
            }
        }

        return result;
    }

    /**
     * Get ByteBuddy class matcher based on include/exclude patterns
     */
    public ElementMatcher.Junction<? super net.bytebuddy.description.type.TypeDescription> getClassMatcher() {
        ElementMatcher.Junction<? super net.bytebuddy.description.type.TypeDescription> matcher = any();

        // Exclude agent classes and JDK classes
        matcher = matcher
                .and(not(nameStartsWith("com.example.vtracer")))
                .and(not(nameStartsWith("java.")))
                .and(not(nameStartsWith("javax.")))
                .and(not(nameStartsWith("sun.")))
                .and(not(nameStartsWith("com.sun.")))
                .and(not(nameStartsWith("jdk.")))
                .and(not(nameStartsWith("net.bytebuddy.")));

        // Apply custom exclude pattern
        if (classExcludePattern != null) {
            matcher = matcher.and(not(nameMatches(classExcludePattern)));
        }

        // Apply custom include pattern
        if (classIncludePattern != null) {
            matcher = matcher.and(nameMatches(classIncludePattern));
        }

        return matcher;
    }

    /**
     * Get ByteBuddy method matcher
     */
    public ElementMatcher.Junction<? super net.bytebuddy.description.method.MethodDescription> getMethodMatcher() {
        return isMethod()
                .and(not(isAbstract()))
                .and(not(isNative()))
                .and(not(isSynthetic()))
                .and(not(nameStartsWith("lambda$")));
    }

    // Getters
    public boolean isEnabled() { return enabled; }
    public double getInitialSampleRate() { return initialSampleRate; }
    public double getTargetOverhead() { return targetOverhead; }
    public int getBufferSize() { return bufferSize; }
    public Pattern getClassIncludePattern() { return classIncludePattern; }
    public Pattern getClassExcludePattern() { return classExcludePattern; }
    public Pattern getMethodIncludePattern() { return methodIncludePattern; }
    public int getMaxStackDepth() { return maxStackDepth; }
    public Path getOutputDir() { return outputDir; }
    public OutputFormat getOutputFormat() { return outputFormat; }
    public int getReportIntervalSeconds() { return reportIntervalSeconds; }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean enabled = true;
        private double initialSampleRate = 0.05; // 5%
        private double targetOverhead = 0.02; // 2%
        private int bufferSize = 65536; // Power of 2
        private Pattern classIncludePattern = null; // null = all
        private Pattern classExcludePattern = null;
        private Pattern methodIncludePattern = Pattern.compile(".*");
        private int maxStackDepth = 512;
        private Path outputDir = Paths.get(System.getProperty("java.io.tmpdir"), "vtracer");
        private OutputFormat outputFormat = OutputFormat.FOLDED;
        private int reportIntervalSeconds = 60;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder initialSampleRate(double rate) {
            if (rate < 0.0 || rate > 1.0) {
                throw new IllegalArgumentException("Sample rate must be between 0.0 and 1.0");
            }
            this.initialSampleRate = rate;
            return this;
        }

        public Builder targetOverhead(double overhead) {
            if (overhead < 0.0 || overhead > 1.0) {
                throw new IllegalArgumentException("Target overhead must be between 0.0 and 1.0");
            }
            this.targetOverhead = overhead;
            return this;
        }

        public Builder bufferSize(int size) {
            // Must be power of 2 for ring buffer
            if (size <= 0 || (size & (size - 1)) != 0) {
                throw new IllegalArgumentException("Buffer size must be power of 2");
            }
            this.bufferSize = size;
            return this;
        }

        public Builder classInclude(String pattern) {
            this.classIncludePattern = Pattern.compile(pattern);
            return this;
        }

        public Builder classExclude(String pattern) {
            this.classExcludePattern = Pattern.compile(pattern);
            return this;
        }

        public Builder methodInclude(String pattern) {
            this.methodIncludePattern = Pattern.compile(pattern);
            return this;
        }

        public Builder maxStackDepth(int depth) {
            this.maxStackDepth = depth;
            return this;
        }

        public Builder outputDir(Path dir) {
            this.outputDir = dir;
            return this;
        }

        public Builder outputFormat(OutputFormat format) {
            this.outputFormat = format;
            return this;
        }

        public Builder reportIntervalSeconds(int seconds) {
            this.reportIntervalSeconds = seconds;
            return this;
        }

        public VTracerConfig build() {
            return new VTracerConfig(this);
        }
    }
}