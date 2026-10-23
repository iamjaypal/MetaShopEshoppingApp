package com.shopMeta.cart_service.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shopMeta.cart_service.config.InternalApiProperties;

import lombok.RequiredArgsConstructor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guards {@code /api/v1/internal/**} with the shared {@code X-Internal-Token} (spec 5.4 / 18).
 *
 * <p>Internal endpoints are called service-to-service (order-service clears a cart after the order
 * is confirmed), never by a browser. The gateway also refuses to route {@code /api/v1/internal/**}
 * from the outside (see D-041), so this is defence in depth for traffic that reaches the service
 * port directly.
 *
 * <p>A request to an internal path without the correct token is rejected here with a 403 — clearer
 * for a caller than letting it fall through to a generic "authentication required".
 */
@RequiredArgsConstructor
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    public static final String INTERNAL_AUTHORITY = "ROLE_INTERNAL";

    private static final String INTERNAL_PATH_PREFIX = "/api/v1/internal/";

    private final InternalApiProperties properties;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!isValid(request.getHeader(INTERNAL_TOKEN_HEADER))) {
            writeForbidden(response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority(INTERNAL_AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /** Constant-time comparison so the token cannot be guessed byte by byte. */
    private boolean isValid(String presented) {
        String expected = properties.getToken();
        if (presented == null || expected == null || expected.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"Missing or invalid X-Internal-Token\",\"data\":null}");
    }
}
