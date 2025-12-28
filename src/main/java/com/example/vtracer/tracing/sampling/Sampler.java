package com.example.vtracer.tracing.sampling;

/**
 * Interface for sampling strategies
 */
public interface Sampler {

    /**
     * Decide whether to sample this call
     *
     * @param stackDepth current stack depth
     * @return true if should sample, false otherwise
     */
    boolean shouldSample(int stackDepth);

    /**
     * Adjust sampling rate based on measured overhead
     *
     * @param measuredOverhead current overhead as fraction (0.0 to 1.0)
     */
    void adjust(double measuredOverhead);

    /**
     * Get current sample rate
     *
     * @return sample rate as fraction (0.0 to 1.0)
     */
    double getSampleRate();
}