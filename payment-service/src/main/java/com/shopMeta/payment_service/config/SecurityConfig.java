package com.shopMeta.payment_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.shopMeta.payment_service.security.InternalTokenFilter;
import com.shopMeta.payment_service.security.JwtAuthenticationFilter;
import com.shopMeta.payment_service.security.JwtService;

import lombok.RequiredArgsConstructor;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Security rules for payment-service.
 *
 * <ul>
 *   <li>{@code /api/v1/payments/**} — JWT; every payment belongs to the caller whose id is in the
 *       token (ownership is re-checked per payment id in the service).</li>
 *   <li>{@code /api/v1/admin/payments/**} — ADMIN only.</li>
 *   <li>{@code /api/v1/internal/**} — reserved for service-to-service callers, guarded by
 *       {@code X-Internal-Token} instead of a JWT.</li>
 *   <li>Swagger / actuator health stay public for development and the docker healthcheck.</li>
 * </ul>
 *
 * <p>CORS is disabled here because the API Gateway is the only browser-facing entry point and owns
 * the CORS policy (see D-045).
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    /** Infrastructure endpoints that must stay reachable without a JWT. */
    private static final String[] PUBLIC_INFRA_ENDPOINTS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/info"
    };

    private final JwtService jwtService;
    private final InternalApiProperties internalApiProperties;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    public InternalTokenFilter internalTokenFilter() {
        return new InternalTokenFilter(internalApiProperties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            InternalTokenFilter internalTokenFilter) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_INFRA_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/internal/**")
                        .hasAuthority(InternalTokenFilter.INTERNAL_AUTHORITY)
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpStatus.UNAUTHORIZED,
                                        "Authentication is required"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpStatus.FORBIDDEN,
                                        "You do not have permission to access this resource")))
                .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\",\"data\":null}");
    }
}
