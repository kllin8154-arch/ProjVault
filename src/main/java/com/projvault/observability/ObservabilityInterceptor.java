package com.projvault.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ObservabilityInterceptor implements HandlerInterceptor {
    private static final String STARTED_AT = ObservabilityInterceptor.class.getName() + ".startedAt";
    private final ObservabilityService service;

    public ObservabilityInterceptor(ObservabilityService service) { this.service = service; }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        request.setAttribute(STARTED_AT, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        Object started = request.getAttribute(STARTED_AT);
        long duration = started instanceof Long value ? (System.nanoTime() - value) / 1_000_000 : 0;
        service.record(request.getMethod(), request.getRequestURI(), response.getStatus(), duration, ex);
    }
}
