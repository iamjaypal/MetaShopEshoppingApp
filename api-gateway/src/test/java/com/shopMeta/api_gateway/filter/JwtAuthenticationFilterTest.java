package com.shopMeta.api_gateway.filter;

import static com.shopMeta.api_gateway.filter.JwtAuthenticationFilter.USER_EMAIL_HEADER;
import static com.shopMeta.api_gateway.filter.JwtAuthenticationFilter.USER_ID_HEADER;
import static com.shopMeta.api_gateway.filter.JwtAuthenticationFilter.USER_ROLES_HEADER;
import static com.shopMeta.api_gateway.security.AuthRequirement.ADMIN;
import static com.shopMeta.api_gateway.security.AuthRequirement.JWT;
import static com.shopMeta.api_gateway.security.AuthRequirement.METADATA_KEY;
import static com.shopMeta.api_gateway.security.AuthRequirement.PUBLIC;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopMeta.api_gateway.config.GatewayProperties;
import com.shopMeta.api_gateway.error.GatewayErrorWriter;
import com.shopMeta.api_gateway.security.AuthRequirement;
import com.shopMeta.api_gateway.security.JwtValidator;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "GatewayUnitTestSecretKeyThatIsDefinitelyLongEnough0123456789";
    private static final String ISSUER = "metashop-auth-service";

    private JwtAuthenticationFilter filter;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        GatewayProperties properties = new GatewayProperties();
        properties.getJwt().setSecret(SECRET);
        properties.getJwt().setIssuer(ISSUER);

        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        filter = new JwtAuthenticationFilter(new JwtValidator(properties), new GatewayErrorWriter(new ObjectMapper()));
    }

    private String token(String userId, String email, List<String> roles, Instant expiry) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("roles", roles)
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    private String validToken(List<String> roles) {
        return token("7", "user@metashop.com", roles, Instant.now().plusSeconds(900));
    }

    private MockServerWebExchange exchange(AuthRequirement requirement, String bearer, String spoofedUserId) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/api/v1/users/me");
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        if (spoofedUserId != null) {
            request.header(USER_ID_HEADER, spoofedUserId);
        }

        MockServerWebExchange exchange = MockServerWebExchange.from(request.build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, Route.async()
                .id("test-route")
                .uri("lb://auth-service")
                .predicate(e -> true)
                .metadata(METADATA_KEY, requirement.name())
                .build());
        return exchange;
    }

    private record ChainResult(boolean invoked, String userId, String email, String roles) {
    }

    private ChainResult run(MockServerWebExchange exchange) {
        AtomicBoolean invoked = new AtomicBoolean(false);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            invoked.set(true);
            forwarded.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        ServerWebExchange ex = forwarded.get();
        return new ChainResult(invoked.get(),
                ex == null ? null : ex.getRequest().getHeaders().getFirst(USER_ID_HEADER),
                ex == null ? null : ex.getRequest().getHeaders().getFirst(USER_EMAIL_HEADER),
                ex == null ? null : ex.getRequest().getHeaders().getFirst(USER_ROLES_HEADER));
    }

    // ---------- public routes ----------

    @Test
    void publicRoutePassesThroughWithoutToken() {
        ChainResult result = run(exchange(PUBLIC, null, null));

        assertThat(result.invoked()).isTrue();
        assertThat(result.userId()).isNull();
    }

    @Test
    void spoofedIdentityHeadersAreAlwaysStripped() {
        ChainResult result = run(exchange(PUBLIC, null, "999"));

        assertThat(result.invoked()).isTrue();
        assertThat(result.userId()).isNull();
    }

    // ---------- JWT routes ----------

    @Test
    void missingTokenIsRejectedWith401() {
        MockServerWebExchange exchange = exchange(JWT, null, null);

        ChainResult result = run(exchange);

        assertThat(result.invoked()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("UNAUTHORIZED");
    }

    @Test
    void malformedTokenIsRejectedWith401() {
        MockServerWebExchange exchange = exchange(JWT, "not-a-jwt", null);

        ChainResult result = run(exchange);

        assertThat(result.invoked()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenIsRejectedWith401() {
        String expired = token("7", "user@metashop.com", List.of("CUSTOMER"), Instant.now().minusSeconds(60));
        MockServerWebExchange exchange = exchange(JWT, expired, null);

        ChainResult result = run(exchange);

        assertThat(result.invoked()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreign = Jwts.builder()
                .subject("7")
                .claim("email", "user@metashop.com")
                .claim("roles", List.of("ADMIN"))
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(
                        "ACompletelyDifferentSecretKeyUsedByAnAttacker0123456789".getBytes(StandardCharsets.UTF_8)))
                .compact();
        MockServerWebExchange exchange = exchange(ADMIN, foreign, null);

        ChainResult result = run(exchange);

        assertThat(result.invoked()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validTokenPropagatesIdentityHeaders() {
        ChainResult result = run(exchange(JWT, validToken(List.of("CUSTOMER")), null));

        assertThat(result.invoked()).isTrue();
        assertThat(result.userId()).isEqualTo("7");
        assertThat(result.email()).isEqualTo("user@metashop.com");
        assertThat(result.roles()).isEqualTo("CUSTOMER");
    }

    // ---------- admin routes ----------

    @Test
    void adminRouteRejectsCustomerWith403() {
        MockServerWebExchange exchange = exchange(ADMIN, validToken(List.of("CUSTOMER")), null);

        ChainResult result = run(exchange);

        assertThat(result.invoked()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("FORBIDDEN");
    }

    @Test
    void adminRouteAcceptsAdminToken() {
        ChainResult result = run(exchange(ADMIN, validToken(List.of("ADMIN")), null));

        assertThat(result.invoked()).isTrue();
        assertThat(result.roles()).isEqualTo("ADMIN");
    }

    @Test
    void adminRouteRejectsMissingTokenWith401() {
        MockServerWebExchange exchange = exchange(ADMIN, null, null);

        ChainResult result = run(exchange);

        assertThat(result.invoked()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

}
