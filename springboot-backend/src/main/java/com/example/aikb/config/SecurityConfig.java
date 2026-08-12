package com.example.aikb.config;

import com.example.aikb.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("不使用默认 UserDetailsService，请通过 JWT 认证");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthSecurityProperties authSecurityProperties
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("""
                                    {"success":false,"message":"请先登录","data":null}
                                    """);
                        })
                )
                .authorizeHttpRequests(auth -> {
                    // StreamingResponseBody 会在初始 JWT 请求通过后触发容器内部 ASYNC 分派。
                    // 此处分派没有新的客户端身份输入，只负责继续写出已授权请求的响应流。
                    auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll();
                    auth.requestMatchers(
                            "/",
                            "/index.html",
                            "/debug.html",
                            "/favicon.ico",
                            "/error",
                            "/api/health",
                            "/api/auth/register",
                            "/api/auth/login"
                    ).permitAll();
                    auth.requestMatchers("/api/auth/me").authenticated();

                    if (authSecurityProperties.allowLegacyIdentityParameters()) {
                        auth.requestMatchers("/api/**").permitAll();
                    } else {
                        auth.requestMatchers("/api/**").authenticated();
                    }

                    auth.anyRequest().permitAll();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
