package com.example.vtracer.tracing.sampling;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Adaptive sampler that adjusts rate based on measured overhead
 *
 * <p>Implements feedback loop: - If overhead > target: decrease sample rate - If overhead < target:
 * increase sample rate
 */
public class AdaptiveSampler implements Sampler {

  private static final int MAX_STACK_DEPTH = 512;
  private static final double OVERHEAD_HIGH_THRESHOLD_MULTIPLIER = 1.5;
  private static final double OVERHEAD_LOW_THRESHOLD_MULTIPLIER = 0.5;
  private static final double HIGH_OVERHEAD_REDUCTION_FACTOR = 0.7;
  private static final double LOW_OVERHEAD_INCREASE_FACTOR = 1.1;
  private static final int ADJUSTMENT_INTERVAL_SECONDS = 5;

  private volatile double sampleRate;
  private final double targetOverhead;
  private final int maxStackDepth;

  public AdaptiveSampler(double initialSampleRate, double targetOverhead) {
    this.sampleRate = initialSampleRate;
    this.targetOverhead = targetOverhead;
    this.maxStackDepth = MAX_STACK_DEPTH;
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
    if (measuredOverhead > targetOverhead * OVERHEAD_HIGH_THRESHOLD_MULTIPLIER) {
      sampleRate = Math.max(0.001, currentRate * HIGH_OVERHEAD_REDUCTION_FACTOR);
      System.out.printf(
          "[VTracer] Overhead high (%.2f%%), reducing sample rate to %.2f%%%n",
          measuredOverhead * 100, sampleRate * 100);
    }
    // If overhead is low, ramp up cautiously
    else if (measuredOverhead < targetOverhead * OVERHEAD_LOW_THRESHOLD_MULTIPLIER) {
      sampleRate = Math.min(1.0, currentRate * LOW_OVERHEAD_INCREASE_FACTOR);
      if (sampleRate != currentRate) {
        System.out.printf(
            "[VTracer] Overhead low (%.2f%%), increasing sample rate to %.2f%%%n",
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
