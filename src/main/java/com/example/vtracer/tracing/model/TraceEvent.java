package com.example.vtracer.tracing.model;

/** Immutable trace event representing a method call */
public class TraceEvent {

  public enum Type {
    ENTER,
    EXIT
  }

  private final long threadId;
  private final String className;
  private final String methodName;
  private final long timestamp;
  private final Type type;

  public TraceEvent(long threadId, String className, String methodName, long timestamp, Type type) {
    this.threadId = threadId;
    this.className = className;
    this.methodName = methodName;
    this.timestamp = timestamp;
    this.type = type;
  }

  public static TraceEvent enter(
      long threadId, String className, String methodName, long timestamp) {
    return new TraceEvent(threadId, className, methodName, timestamp, Type.ENTER);
  }

  public static TraceEvent exit(
      long threadId, String className, String methodName, long timestamp) {
    return new TraceEvent(threadId, className, methodName, timestamp, Type.EXIT);
  }

  public long getThreadId() {
    return threadId;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodName() {
    return methodName;
  }

  public String getMethodSignature() {
    return className + "." + methodName;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Type getType() {
    return type;
  }

  public boolean isEnter() {
    return type == Type.ENTER;
  }

  public boolean isExit() {
    return type == Type.EXIT;
  }

  @Override
  public String toString() {
    return String.format(
        "TraceEvent{thread=%d, method=%s.%s, type=%s, timestamp=%d}",
        threadId, className, methodName, type, timestamp);
  }
}
