package com.yunfeil161.ailibrary.config.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jwt.interceptor-enabled", havingValue = "true")
public class WebConfig implements WebMvcConfigurer {
    private final TokenRefreshInterceptor tokenRefreshInterceptor;
    private final JwtInterceptor jwtInterceptor;
    private final com.yunfeil161.ailibrary.policy.interceptor.RuleConsentInterceptor ruleConsentInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenRefreshInterceptor)
                .addPathPatterns("/**")
                .order(0);

        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/login/code",
                        "/login/register",
                        "/login/reset-password",
                        // WebSocket握手由一次性票据独立认证。
                        "/ws/events",
                        "/error")
                .order(1);
        registry.addInterceptor(ruleConsentInterceptor).addPathPatterns("/**").order(2);
    }
}
