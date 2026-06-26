package com.projvault.security;

import com.projvault.ai.AiCallerContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AiCallerContextInterceptor implements HandlerInterceptor {
    private final AuthService authService;

    public AiCallerContextInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        AiCallerContext.clear();
        authService.currentUser(request).ifPresent(user -> AiCallerContext.set(
                user.getId(), user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getCode()))));
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        AiCallerContext.clear();
    }
}
