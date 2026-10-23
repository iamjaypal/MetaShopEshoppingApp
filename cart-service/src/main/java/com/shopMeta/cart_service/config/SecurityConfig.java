package com.shopMeta.cart_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.shopMeta.cart_service.security.InternalTokenFilter;
import com.shopMeta.cart_service.security.JwtAuthenticationFilter;
import com.shopMeta.cart_service.security.JwtService;

import lombok.RequiredArgsConstructor;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Security rules for cart-service.
 *
 * <ul>
 *   <li>Every {@code /api/v1/cart/**} endpoint requires a JWT: a cart always belongs to the caller
 *       whose user id is in the token, and the platform has no anonymous identity yet (guest carts
 *       need one, so they are out of scope).</li>
 *   <li>{@code /api/v1/internal/cart/**} is service-to-service only, guarded by
 *       {@code X-Internal-Token} instead of a JWT (order-service clears the cart after
 *       confirmation). Not routed by the gateway (D-041) — this is defence in depth.</li>
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
