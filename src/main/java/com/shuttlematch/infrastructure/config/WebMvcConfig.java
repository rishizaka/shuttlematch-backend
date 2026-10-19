package com.shuttlematch.infrastructure.config;

import com.shuttlematch.presentation.api.OrganizerAuthInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC のインターセプタ登録。運営者トークンの検証を全 API に噛ませる
 * (実際に検証されるのは {@code @RequiresOrganizer} が付いたハンドラのみ)。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final OrganizerAuthInterceptor organizerAuthInterceptor;

    public WebMvcConfig(OrganizerAuthInterceptor organizerAuthInterceptor) {
        this.organizerAuthInterceptor = organizerAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(organizerAuthInterceptor).addPathPatterns("/api/**");
    }
}
