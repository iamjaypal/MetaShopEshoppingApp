package com.shopMeta.auth_service.service;

import com.shopMeta.auth_service.config.SecurityProperties;
import com.shopMeta.auth_service.dto.ForgotPasswordRequest;
import com.shopMeta.auth_service.dto.ResetPasswordRequest;
import com.shopMeta.auth_service.entity.PasswordResetToken;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.exception.InvalidTokenException;
import com.shopMeta.auth_service.repository.PasswordResetTokenRepository;
import com.shopMeta.auth_service.repository.UserRepository;
import com.shopMeta.auth_service.util.SecureTokens;
import com.shopMeta.auth_service.util.TokenHasher;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Forgot/reset password (spec 7.3).
 *
 * <p>The reset token is single-use, stored hashed and expires after
 * {@code metashop.security.password-reset-ttl}. Requesting a new token invalidates any previous
 * unused one. The {@code PasswordResetRequestedEvent} (spec 14.3) is published in the Kafka
 * phase; until then the reset link is logged outside the {@code prod} profile.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final SecurityProperties securityProperties;
    private final Environment environment;

    /**
     * Starts a password reset. Always succeeds from the caller's point of view so the endpoint
     * never reveals whether an email is registered.
     */
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        userRepository.findByEmail(email).ifPresent(user -> {

            passwordResetTokenRepository.invalidateAllForUser(user.getId());

            String rawToken = SecureTokens.randomToken();

            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(TokenHasher.sha256Hex(rawToken))
                    .expiresAt(LocalDateTime.now().plus(securityProperties.getPasswordResetTtl()))
                    .used(false)
                    .build());

            if (!environment.matchesProfiles("prod")) {
                log.info("Password reset token for {} (dev only, not sent by email yet): {}",
                        user.getEmail(), rawToken);
            }
        });
    }

    /**
     * Completes a password reset: validates the token, sets the new password, marks the token
     * used, clears any lockout and revokes all refresh tokens.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(request.getToken()))
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired reset token"));

        if (Boolean.TRUE.equals(token.getUsed())) {
            throw new InvalidTokenException("This reset token has already been used");
        }

        if (token.isExpired()) {
            token.setUsed(true);
            passwordResetTokenRepository.save(token);
            throw new InvalidTokenException("This reset token has expired");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        refreshTokenService.revokeAllForUser(user.getId());
    }
}
