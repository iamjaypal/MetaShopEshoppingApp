package com.shopMeta.cart_service.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.shopMeta.cart_service.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Verifies access tokens issued by auth-service (spec 5.4).
 *
 * <p>cart-service never issues tokens; it only checks the signature, issuer and expiry with the
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

    /**
     * Builds the caller identity from the claims, or {@code null} when the token has no usable
     * subject.
     */
    public AuthenticatedUser toAuthenticatedUser(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            return null;
        }

        return new AuthenticatedUser(
                parseUserId(subject),
                claims.get("email", String.class),
                extractRoles(claims));
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException ex) {
            // Subject is not a numeric user id; the identity is still usable for the email/roles.
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
