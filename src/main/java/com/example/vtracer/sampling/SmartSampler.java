package com.example.vtracer.sampling;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class SmartSampler {
    private static double samplingRate = 0.1; // 10% default
    private static final ThreadLocal<Random> random =
            ThreadLocal.withInitial(Random::new);
    private static final AtomicLong sampledCount = new AtomicLong();
    private static final AtomicLong totalCount = new AtomicLong();

    public static boolean shouldSample() {
        totalCount.incrementAndGet();

        if (random.get().nextDouble() < samplingRate) {
            sampledCount.incrementAndGet();
            return true;
        }
        return false;
    }

    public static void setSamplingRate(double rate) {
        if (rate < 0 || rate > 1) {
            throw new IllegalArgumentException("Sampling rate must be between 0 and 1");
        }
        samplingRate = rate;
    }

    public static double getSamplingRate() {
        return samplingRate;
    }

    public static double getActualRate() {
        long total = totalCount.get();
        return total == 0 ? 0 : (double) sampledCount.get() / total;
    }

    public static long getSampledCount() {
        return sampledCount.get();
    }

    public static long getTotalCount() {
        return totalCount.get();
    }

    public static void reset() {
        sampledCount.set(0);
        totalCount.set(0);
    }
}