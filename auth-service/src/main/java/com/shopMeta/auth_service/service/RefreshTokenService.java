package com.shopMeta.auth_service.service;

import com.shopMeta.auth_service.config.JwtProperties;
import com.shopMeta.auth_service.entity.RefreshToken;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.exception.InvalidTokenException;
import com.shopMeta.auth_service.repository.RefreshTokenRepository;
import com.shopMeta.auth_service.security.JwtService;
import com.shopMeta.auth_service.util.TokenHasher;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Issues, rotates and revokes refresh tokens (spec 7.3/7.4).
 *
 * <p>Only SHA-256 hashes are stored. Every use rotates the token; presenting an already
 * revoked token is treated as theft and revokes all of that user's tokens.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    /**
     * Creates a new refresh token for the user and returns the raw (unhashed) value.
     */
    @Transactional
    public String issue(User user) {

        String rawToken = jwtService.generateRefreshTokenValue();

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().plus(jwtProperties.getRefreshTokenTtl()))
                .revoked(false)
                .build());

        return rawToken;
    }

    /**
     * Validates a refresh token and rotates it (revokes the old one, issues a new one).
     *
     * @return the new raw refresh-token value
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public RotationResult rotate(String rawToken) {

        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (Boolean.TRUE.equals(stored.getRevoked())) {
            // Reuse of a revoked token: assume compromise and revoke the whole family.
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId());
            throw new InvalidTokenException("Refresh token has already been used");
        }

        if (stored.isExpired()) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new InvalidTokenException("Refresh token has expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        String newRawToken = issue(user);

        return new RotationResult(user, newRawToken);
    }

    /**
     * Revokes the given refresh token (logout).
     */
    @Transactional
    public void revoke(String rawToken) {

        refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    /**
     * The user the rotated token belongs to, plus the newly issued raw token.
     */
    public record RotationResult(User user, String refreshToken) {
    }
}
