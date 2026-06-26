package com.projvault.security;

import com.projvault.observability.ObservabilityInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：注册权限拦截器，作用于所有 /api/** 接口。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;
    private final ObservabilityInterceptor observabilityInterceptor;
    private final ProjectScopeInterceptor projectScopeInterceptor;
    private final AiCallerContextInterceptor aiCallerContextInterceptor;

    public WebMvcConfig(PermissionInterceptor permissionInterceptor,
                        ObservabilityInterceptor observabilityInterceptor,
                        ProjectScopeInterceptor projectScopeInterceptor,
                        AiCallerContextInterceptor aiCallerContextInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
        this.observabilityInterceptor = observabilityInterceptor;
        this.projectScopeInterceptor = projectScopeInterceptor;
        this.aiCallerContextInterceptor = aiCallerContextInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(aiCallerContextInterceptor).addPathPatterns("/api/**").order(-1);
        registry.addInterceptor(observabilityInterceptor).addPathPatterns("/api/**").order(0);
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/**").order(1);
        registry.addInterceptor(projectScopeInterceptor).addPathPatterns("/api/pkc/**").order(2);
    }
}
