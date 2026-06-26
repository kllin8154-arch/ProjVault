package com.projvault.observability;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ObservabilityService {
    private static final int FAILURE_LIMIT = 30;
    private final Map<String, MutableMetric> routes = new ConcurrentHashMap<>();
    private final Deque<RecentFailure> failures = new ArrayDeque<>();

    public void record(String method, String route, int status, long durationMs, Throwable error) {
        String key = method + " " + normalizeRoute(route);
        routes.computeIfAbsent(key, ignored -> new MutableMetric()).record(status, durationMs);
        if (status >= 500 || error != null) {
            synchronized (failures) {
                failures.addFirst(new RecentFailure(LocalDateTime.now(), key, status,
                        error == null ? "HTTP " + status : safeMessage(error)));
                while (failures.size() > FAILURE_LIMIT) failures.removeLast();
            }
        }
    }

    public ObservabilitySnapshot snapshot() {
        List<RouteMetric> routeMetrics = routes.entrySet().stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparingLong(RouteMetric::requests).reversed())
                .toList();
        long requests = routeMetrics.stream().mapToLong(RouteMetric::requests).sum();
        long errors = routeMetrics.stream().mapToLong(RouteMetric::errors).sum();
        long totalDuration = routeMetrics.stream().mapToLong(metric -> metric.requests() * metric.averageMs()).sum();
        Runtime runtime = Runtime.getRuntime();
        List<RecentFailure> recent;
        synchronized (failures) { recent = List.copyOf(failures); }
        return new ObservabilitySnapshot(LocalDateTime.now(), requests, errors,
                requests == 0 ? 0 : totalDuration / requests,
                runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory(),
                routeMetrics, recent);
    }

    private String normalizeRoute(String route) {
        if (route == null) return "unknown";
        return route.replaceAll("/[0-9]+(?=/|$)", "/{id}")
                .replaceAll("/[0-9a-fA-F-]{24,}(?=/|$)", "/{key}");
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName()
                : message.substring(0, Math.min(500, message.length()));
    }

    private static final class MutableMetric {
        private final AtomicLong requests = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();
        private final AtomicLong totalMs = new AtomicLong();
        private final AtomicLong maxMs = new AtomicLong();
        void record(int status, long durationMs) {
            requests.incrementAndGet();
            if (status >= 400) errors.incrementAndGet();
            totalMs.addAndGet(durationMs);
            maxMs.accumulateAndGet(durationMs, Math::max);
        }
        RouteMetric snapshot(String route) {
            long count = requests.get();
            return new RouteMetric(route, count, errors.get(), count == 0 ? 0 : totalMs.get() / count, maxMs.get());
        }
    }

    public record ObservabilitySnapshot(LocalDateTime capturedAt, long requests, long errors,
                                        long averageMs, long usedMemoryBytes, long maxMemoryBytes,
                                        List<RouteMetric> routes, List<RecentFailure> recentFailures) {}
    public record RouteMetric(String route, long requests, long errors, long averageMs, long maxMs) {}
    public record RecentFailure(LocalDateTime occurredAt, String route, int status, String message) {}
}
