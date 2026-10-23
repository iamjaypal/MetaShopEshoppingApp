package com.shopMeta.api_gateway.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.shopMeta.api_gateway.config.GatewayProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Verifies access tokens issued by auth-service using the shared HS256 secret (spec 5.4).
 *
 * <p>Only the signature and the expiry are checked here; the token is then trusted for routing
 * purposes. Downstream services still validate it again (defence in depth).
 */
@Component
public class JwtValidator {

    private final SecretKey signingKey;
    private final String expectedIssuer;

    public JwtValidator(GatewayProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(
                properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.expectedIssuer = properties.getJwt().getIssuer();
    }

    /**
     * @throws io.jsonwebtoken.JwtException if the token is malformed, tampered with or expired
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(expectedIssuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
