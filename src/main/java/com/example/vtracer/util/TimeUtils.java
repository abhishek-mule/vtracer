package com.example.vtracer.util;

/** Time conversion utilities */
public class TimeUtils {

  /** Convert nanoseconds to milliseconds */
  public static long nanosToMillis(long nanos) {
    return nanos / 1_000_000;
  }

  /** Convert nanoseconds to microseconds */
  public static long nanosToMicros(long nanos) {
    return nanos / 1_000;
  }

  /** Convert nanoseconds to seconds */
  public static double nanosToSeconds(long nanos) {
    return nanos / 1_000_000_000.0;
  }

  /** Format nanoseconds as human-readable string */
  public static String formatNanos(long nanos) {
    if (nanos < 1_000) {
      return nanos + "ns";
    } else if (nanos < 1_000_000) {
      return String.format("%.2fµs", nanos / 1_000.0);
    } else if (nanos < 1_000_000_000) {
      return String.format("%.2fms", nanos / 1_000_000.0);
    } else {
      return String.format("%.2fs", nanos / 1_000_000_000.0);
    }
  }
}
