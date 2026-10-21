package com.yunfeil161.ailibrary.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Web 安全基础配置。
 *
 * <p>Spring Security 负责关闭表单登录、HTTP Basic 和服务端 Session。
 * JWT 解析、Redis 会话检查及角色权限判断由项目现有的两层 MVC 拦截器和
 * PermissionAccess 完成，因此过滤器链允许请求继续进入这些拦截器。</p>
 */
@Configuration
public class SecurityConfig {

    /**
     * 注册密码摘要编码器，用于生成和校验 BCrypt 密码摘要。
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置无状态 HTTP 基础策略；具体路径认证由 MVC 拦截器统一处理。
     *
     * @param http Spring Security HTTP 配置器
     * @return 安全过滤器链
     * @throws Exception 安全配置构建失败时抛出
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll());

        return http.build();
    }
}
