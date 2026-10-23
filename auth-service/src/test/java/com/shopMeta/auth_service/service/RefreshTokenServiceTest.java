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

import com.shopMeta.auth_service.config.JwtProperties;
import com.shopMeta.auth_service.entity.RefreshToken;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.exception.InvalidTokenException;
import com.shopMeta.auth_service.repository.RefreshTokenRepository;
import com.shopMeta.auth_service.security.JwtService;
import com.shopMeta.auth_service.util.TokenHasher;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user() {
        return User.builder().id(1L).email("test@example.com").build();
    }

    private RefreshToken token(String rawToken, User user, boolean revoked, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(TokenHasher.sha256Hex(rawToken))
                .revoked(revoked)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void issue_storesOnlyTheHash() {

        given(jwtService.generateRefreshTokenValue()).willReturn("raw-token");
        given(jwtProperties.getRefreshTokenTtl()).willReturn(Duration.ofDays(7));

        String issued = refreshTokenService.issue(user());

        assertThat(issued).isEqualTo("raw-token");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(TokenHasher.sha256Hex("raw-token"));
        assertThat(saved.getTokenHash()).isNotEqualTo("raw-token");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    void rotate_validToken_revokesOldAndIssuesNew() {

        User user = user();
        RefreshToken stored = token("old", user, false, LocalDateTime.now().plusDays(1));

        given(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex("old")))
                .willReturn(Optional.of(stored));
        given(jwtService.generateRefreshTokenValue()).willReturn("new");
        given(jwtProperties.getRefreshTokenTtl()).willReturn(Duration.ofDays(7));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate("old");

        assertThat(result.user()).isEqualTo(user);
        assertThat(result.refreshToken()).isEqualTo("new");
        assertThat(stored.getRevoked()).isTrue();
    }

    @Test
    void rotate_revokedToken_revokesTheWholeFamily() {

        RefreshToken stored = token("stolen", user(), true, LocalDateTime.now().plusDays(1));

        given(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex("stolen")))
                .willReturn(Optional.of(stored));

        assertThatThrownBy(() -> refreshTokenService.rotate("stolen"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("already been used");

        verify(refreshTokenRepository).revokeAllForUser(1L);
    }

    @Test
    void rotate_expiredToken_isRejectedAndMarkedRevoked() {

        RefreshToken stored = token("expired", user(), false, LocalDateTime.now().minusMinutes(1));

        given(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex("expired")))
                .willReturn(Optional.of(stored));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");

        assertThat(stored.getRevoked()).isTrue();
        verify(refreshTokenRepository, never()).revokeAllForUser(any());
    }

    @Test
    void rotate_unknownToken_isRejected() {

        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("nope"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void revoke_marksTheTokenRevoked() {

        RefreshToken stored = token("live", user(), false, LocalDateTime.now().plusDays(1));

        given(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex("live")))
                .willReturn(Optional.of(stored));

        refreshTokenService.revoke("live");

        assertThat(stored.getRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void revoke_unknownToken_isSilentlyIgnored() {

        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        refreshTokenService.revoke("nope");

        verify(refreshTokenRepository, never()).save(any());
    }
}
