package com.shopMeta.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.shopMeta.auth_service.config.SecurityProperties;
import com.shopMeta.auth_service.dto.ForgotPasswordRequest;
import com.shopMeta.auth_service.dto.ResetPasswordRequest;
import com.shopMeta.auth_service.entity.PasswordResetToken;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.exception.InvalidTokenException;
import com.shopMeta.auth_service.repository.PasswordResetTokenRepository;
import com.shopMeta.auth_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private Environment environment;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User user() {
        return User.builder()
                .id(1L)
                .email("test@example.com")
                .passwordHash("old-hash")
                .enabled(true)
                .failedLoginAttempts(3)
                .lockedUntil(LocalDateTime.now().plusMinutes(5))
                .build();
    }

    private ForgotPasswordRequest forgot(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

    private ResetPasswordRequest reset(String token, String newPassword) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(token);
        request.setNewPassword(newPassword);
        return request;
    }

    @Test
    void requestReset_knownEmail_storesHashedToken() {

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user()));
        given(securityProperties.getPasswordResetTtl()).willReturn(Duration.ofMinutes(30));
        given(environment.matchesProfiles("prod")).willReturn(false);

        passwordResetService.requestReset(forgot("Test@Example.com"));

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());

        PasswordResetToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).hasSize(64);
        assertThat(saved.getUsed()).isFalse();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(29));
        verify(passwordResetTokenRepository).invalidateAllForUser(1L);
    }

    @Test
    void requestReset_unknownEmail_doesNothing() {

        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

        passwordResetService.requestReset(forgot("nobody@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void resetPassword_unknownToken_isRejected() {

        given(passwordResetTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(reset("bad", "N3w@Passw0rd")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void resetPassword_alreadyUsedToken_isRejected() {

        PasswordResetToken token = PasswordResetToken.builder()
                .id(1L)
                .user(user())
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .used(true)
                .build();

        given(passwordResetTokenRepository.findByTokenHash(any())).willReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword(reset("used", "N3w@Passw0rd")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void resetPassword_expiredToken_isRejectedAndConsumed() {

        PasswordResetToken token = PasswordResetToken.builder()
                .id(1L)
                .user(user())
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .used(false)
                .build();

        given(passwordResetTokenRepository.findByTokenHash(any())).willReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword(reset("expired", "N3w@Passw0rd")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");

        assertThat(token.getUsed()).isTrue();
    }

    @Test
    void resetPassword_success_updatesPasswordClearsLockAndRevokesSessions() {

        User user = user();
        PasswordResetToken token = PasswordResetToken.builder()
                .id(1L)
                .user(user)
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();

        given(passwordResetTokenRepository.findByTokenHash(any())).willReturn(Optional.of(token));
        given(passwordEncoder.encode("N3w@Passw0rd")).willReturn("new-hash");

        passwordResetService.resetPassword(reset("good", "N3w@Passw0rd"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(token.getUsed()).isTrue();
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllForUser(1L);
    }
}
