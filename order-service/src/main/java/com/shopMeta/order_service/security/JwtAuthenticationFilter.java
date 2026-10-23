package com.shopMeta.order_service.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header and populates the security context
 * (spec 5.4).
 *
 * <p>Like inventory/cart this filter needs no database: the principal is built straight from the
 * token claims. Invalid or expired tokens are ignored and the request continues unauthenticated,
 * where the authorization rules reject it with a 401.
 *
 * <p>The {@code roles} claim holds plain names ({@code ADMIN}, {@code CUSTOMER}) while Spring
 * Security expects the {@code ROLE_} prefix, so it is added here (see D-035).
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                Claims claims = jwtService.parse(authHeader.substring(BEARER_PREFIX.length()));

                String subject = claims.getSubject();
                List<String> roles = extractRoles(claims);

                if (subject != null && !subject.isBlank()) {
                    Long userId = parseUserId(subject);

                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                            .toList();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    new AuthenticatedUser(userId, claims.get("email", String.class), roles),
                                    null, authorities);

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return (List<String>) list;
        }
        if (roles instanceof String csv && !csv.isBlank()) {
            return List.of(csv.split(","));
        }
        return List.of();
    }
}
