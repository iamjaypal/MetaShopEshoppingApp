package com.shopMeta.api_gateway.filter;

import static com.shopMeta.api_gateway.security.AuthRequirement.ADMIN;
import static com.shopMeta.api_gateway.security.AuthRequirement.JWT;
import static com.shopMeta.api_gateway.security.AuthRequirement.METADATA_KEY;
import static com.shopMeta.api_gateway.security.AuthRequirement.PUBLIC;

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.shopMeta.api_gateway.error.GatewayErrorWriter;
import com.shopMeta.api_gateway.security.AuthRequirement;
import com.shopMeta.api_gateway.security.JwtValidator;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 300;

    private final JwtValidator jwtValidator;
    private final GatewayErrorWriter errorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // Never trust identity headers coming from the outside.
        ServerHttpRequest.Builder request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_EMAIL_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                });

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            // No route matched; let the error handler produce the 404.
            return chain.filter(exchange.mutate().request(request.build()).build());
        }

        AuthRequirement requirement = resolveRequirement(route);

        if (requirement == PUBLIC) {
            return chain.filter(exchange.mutate().request(request.build()).build());
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Authentication is required to access this resource");
        }

        Claims claims;
        try {
            claims = jwtValidator.parse(authorization.substring(BEARER_PREFIX.length()));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected token on {} {}: {}", exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getPath(), ex.getMessage());
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Invalid or expired access token");
        }

        List<String> roles = resolveRoles(claims);
        if (requirement == ADMIN && !roles.contains("ADMIN")) {
            return errorWriter.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "You do not have permission to access this resource");
        }

        request.header(USER_ID_HEADER, claims.getSubject())
                .header(USER_EMAIL_HEADER, claims.get("email", String.class))
                .header(USER_ROLES_HEADER, String.join(",", roles));

        return chain.filter(exchange.mutate().request(request.build()).build());
    }

    private AuthRequirement resolveRequirement(Route route) {
        Object value = route.getMetadata().get(METADATA_KEY);
        if (value == null) {
            // Fail closed: a route without metadata still requires a token.
            return JWT;
        }
        try {
            return AuthRequirement.valueOf(value.toString());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown auth requirement '{}' on route {}; falling back to JWT", value, route.getId());
            return JWT;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return (List<String>) list;
        }
        if (roles instanceof String csv && !csv.isBlank()) {
            return List.of(csv.split(","));
        }
        return List.of();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

}
