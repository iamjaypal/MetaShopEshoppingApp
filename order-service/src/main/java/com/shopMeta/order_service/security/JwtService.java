package com.shopMeta.order_service.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.shopMeta.order_service.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Verifies access tokens issued by auth-service (spec 5.4).
 *
 * <p>order-service never issues tokens; it only checks the signature, issuer and expiry with the
 * shared HS256 secret, then reads the identity out of the claims. Downstream services validate the
 * token themselves rather than trusting the gateway's {@code X-User-*} headers (defence in depth).
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String expectedIssuer;

    public JwtService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(
                properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expectedIssuer = properties.getIssuer();
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
