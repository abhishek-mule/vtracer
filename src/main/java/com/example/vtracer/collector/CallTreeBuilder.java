package com.example.vtracer.collector;

import com.example.vtracer.model.TraceEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CallTreeBuilder {
    private static final ThreadLocal<Deque<TraceEvent>> callStack =
            ThreadLocal.withInitial(ArrayDeque::new);

    private static final Map<Long, List<TraceEvent>> roots =
            new ConcurrentHashMap<>();

    private static volatile boolean enabled = true;

    public static void enterMethod(String className, String methodName,
                                   String signature, long startTime) {
        if (!enabled) return;

        try {
            Deque<TraceEvent> stack = callStack.get();
            TraceEvent parent = stack.isEmpty() ? null : stack.peek();

            TraceEvent event = new TraceEvent(
                    className, methodName, signature,
                    Thread.currentThread().getId(),
                    Thread.currentThread().getName(),
                    startTime,
                    parent,
                    stack.size()
            );

            if (parent != null) {
                parent.addChild(event);
            } else {
                roots.computeIfAbsent(Thread.currentThread().getId(),
                        k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(event);
            }

            stack.push(event);
        } catch (Exception e) {
            System.err.println("[vtracer] Error in enterMethod: " + e.getMessage());
        }
    }

    public static void exitMethod(long endTime) {
        if (!enabled) return;

        try {
            Deque<TraceEvent> stack = callStack.get();
            if (!stack.isEmpty()) {
                TraceEvent event = stack.pop();
                event.setEndTime(endTime);

                long childrenTime = event.getChildren().stream()
                        .mapToLong(TraceEvent::getDuration)
                        .sum();
                event.setSelfTime(Math.max(0, event.getDuration() - childrenTime));
            }
        } catch (Exception e) {
            System.err.println("[vtracer] Error in exitMethod: " + e.getMessage());
        }
    }

    public static Map<Long, List<TraceEvent>> getRoots() {
        Map<Long, List<TraceEvent>> copy = new HashMap<>();
        roots.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    public static void clear() {
        callStack.remove();
        roots.clear();
    }

    public static void setEnabled(boolean enabled) {
        CallTreeBuilder.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int getTotalRoots() {
        return roots.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    public static int getThreadCount() {
        return roots.size();
    }
}