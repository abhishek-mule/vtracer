package com.example.vtracer.tracing.sampling;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Adaptive sampler that adjusts rate based on measured overhead
 *
 * Implements feedback loop:
 * - If overhead > target: decrease sample rate
 * - If overhead < target: increase sample rate
 */
public class AdaptiveSampler implements Sampler {

    private volatile double sampleRate;
    private final double targetOverhead;
    private final int maxStackDepth;

    public AdaptiveSampler(double initialSampleRate, double targetOverhead) {
        this.sampleRate = initialSampleRate;
        this.targetOverhead = targetOverhead;
        this.maxStackDepth = 512;
    }

    @Override
    public boolean shouldSample(int stackDepth) {
        // Never sample beyond max depth (protect against runaway recursion)
        if (stackDepth >= maxStackDepth) {
            return false;
        }

        // Probabilistic sampling
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    @Override
    public void adjust(double measuredOverhead) {
        double currentRate = sampleRate;

        // If overhead is too high, back off aggressively
        if (measuredOverhead > targetOverhead * 1.5) {
            sampleRate = Math.max(0.001, currentRate * 0.7); // Reduce by 30%
            System.out.printf("[VTracer] Overhead high (%.2f%%), reducing sample rate to %.2f%%%n",
                    measuredOverhead * 100, sampleRate * 100);
        }
        // If overhead is low, ramp up cautiously
        else if (measuredOverhead < targetOverhead * 0.5) {
            sampleRate = Math.min(1.0, currentRate * 1.1); // Increase by 10%
            if (sampleRate != currentRate) {
                System.out.printf("[VTracer] Overhead low (%.2f%%), increasing sample rate to %.2f%%%n",
                        measuredOverhead * 100, sampleRate * 100);
            }
        }
        // Otherwise, maintain current rate
    }

    @Override
    public double getSampleRate() {
        return sampleRate;
    }
}