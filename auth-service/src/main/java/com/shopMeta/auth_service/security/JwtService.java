package com.shopMeta.auth_service.security;

import com.shopMeta.auth_service.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import com.shopMeta.auth_service.util.SecureTokens;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and validates JWTs (spec 7.4). Access tokens carry {@code sub} (userId),
 * {@code email}, {@code roles}, {@code iat}, {@code exp} and {@code jti}.
 */
@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Builds a signed access token for the given principal.
     */
    public String generateAccessToken(UserDetails userDetails) {

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());

        return Jwts.builder()
                .subject(resolveUserId(userDetails))
                .claim("email", userDetails.getUsername())
                .claim("roles", resolveRoles(userDetails))
                .id(UUID.randomUUID().toString())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates an opaque, cryptographically random refresh-token value. Only its hash is stored.
     */
    public String generateRefreshTokenValue() {
        return SecureTokens.randomToken();
    }

    public String extractUsername(String token) {
        return parseClaims(token).get("email", String.class);
    }

    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {

        Claims claims = parseClaims(token);

        return claims.get("email", String.class).equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date());
    }

    public long getAccessTokenTtlSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String resolveUserId(UserDetails userDetails) {

        if (userDetails instanceof CustomUserDetails customUserDetails
                && customUserDetails.getUser() != null) {
            return String.valueOf(customUserDetails.getUser().getId());
        }

        return userDetails.getUsername();
    }

    /**
     * The JWT {@code roles} claim carries plain role names (CUSTOMER, ADMIN), not the
     * {@code ROLE_}-prefixed Spring authorities.
     */
    private List<String> resolveRoles(UserDetails userDetails) {

        if (userDetails instanceof CustomUserDetails customUserDetails) {
            return List.copyOf(customUserDetails.getRoleNames());
        }

        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
