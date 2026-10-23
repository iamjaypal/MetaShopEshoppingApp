package com.shopMeta.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.shopMeta.auth_service.config.SecurityProperties;
import com.shopMeta.auth_service.dto.LoginRequest;
import com.shopMeta.auth_service.dto.LoginResponse;
import com.shopMeta.auth_service.dto.RegisterRequest;
import com.shopMeta.auth_service.dto.RegisterResponse;
import com.shopMeta.auth_service.entity.Role;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.entity.Enums.RoleName;
import com.shopMeta.auth_service.exception.AccountDisabledException;
import com.shopMeta.auth_service.exception.AccountLockedException;
import com.shopMeta.auth_service.exception.EmailAlreadyExistsException;
import com.shopMeta.auth_service.exception.InvalidCredentialsException;
import com.shopMeta.auth_service.repository.RoleRepository;
import com.shopMeta.auth_service.repository.UserRepository;
import com.shopMeta.auth_service.security.CustomUserDetails;
import com.shopMeta.auth_service.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private SecurityProperties securityProperties;

    @InjectMocks
    private AuthService authService;

    // ---------------------------------------------------------------- helpers

    private User user(Long id, boolean enabled, Integer failedAttempts, LocalDateTime lockedUntil) {

        return User.builder()
                .id(id)
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .passwordHash("hash")
                .enabled(enabled)
                .emailVerified(false)
                .failedLoginAttempts(failedAttempts)
                .lockedUntil(lockedUntil)
                .roles(new HashSet<>(Set.of(
                        Role.builder().id(1L).name(RoleName.CUSTOMER).build())))
                .build();
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private RegisterRequest registerRequest(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("New");
        request.setLastName("User");
        request.setEmail(email);
        request.setPhone("+919000000001");
        request.setPassword(password);
        return request;
    }

    // ---------------------------------------------------------------- register

    @Test
    void register_createsCustomerWithLowercasedEmail() {

        given(userRepository.existsByEmail("new@example.com")).willReturn(false);
        given(roleRepository.findByName(RoleName.CUSTOMER))
                .willReturn(Optional.of(Role.builder().id(1L).name(RoleName.CUSTOMER).build()));
        given(passwordEncoder.encode("Secret@123")).willReturn("hashed");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        RegisterResponse response = authService.register(registerRequest("New@Example.COM", "Secret@123"));

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getPhone()).isEqualTo("+919000000001");
        assertThat(response.getFirstName()).isEqualTo("New");
    }

    @Test
    void register_duplicateEmail_isRejected() {

        given(userRepository.existsByEmail("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest("Dup@example.com", "Secret@123")))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("already registered");

        verify(roleRepository, never()).findByName(any());
        verify(userRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- login

    @Test
    void login_returnsTokenPairAndUser() {

        User user = user(1L, true, 0, null);
        CustomUserDetails principal = new CustomUserDetails(user);

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(authenticationManager.authenticate(any()))
                .willReturn(new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
        given(jwtService.generateAccessToken(principal)).willReturn("access-token");
        given(refreshTokenService.issue(user)).willReturn("refresh-token");
        given(jwtService.getAccessTokenTtlSeconds()).willReturn(900L);

        LoginResponse response = authService.login(loginRequest("Test@Example.com", "Secret@123"));

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900L);
        assertThat(response.getUser().getId()).isEqualTo(1L);
        assertThat(response.getUser().getRoles()).containsExactly("CUSTOMER");
    }

    @Test
    void login_unknownEmail_returnsGenericFailure() {

        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("nobody@example.com", "Secret@123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void login_badCredentials_incrementsFailedAttempts() {

        User user = user(1L, true, 0, null);

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("bad credentials"));
        given(securityProperties.getMaxFailedLoginAttempts()).willReturn(5);

        assertThatThrownBy(() -> authService.login(loginRequest("test@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void login_locksAccountAfterMaxFailedAttempts() {

        User user = user(1L, true, 4, null);

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("bad credentials"));
        given(securityProperties.getMaxFailedLoginAttempts()).willReturn(5);
        given(securityProperties.getLockDuration()).willReturn(Duration.ofMinutes(15));

        assertThatThrownBy(() -> authService.login(loginRequest("test@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(14));
    }

    @Test
    void login_lockedAccount_isRejectedBeforeAuthentication() {

        User user = user(1L, true, 0, LocalDateTime.now().plusMinutes(10));

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(loginRequest("test@example.com", "Secret@123")))
                .isInstanceOf(AccountLockedException.class);

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void login_disabledAccount_isRejected() {

        User user = user(1L, false, 0, null);

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(authenticationManager.authenticate(any()))
                .willThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> authService.login(loginRequest("test@example.com", "Secret@123")))
                .isInstanceOf(AccountDisabledException.class);
    }

    @Test
    void login_successfulResetsFailedAttempts() {

        User user = user(1L, true, 3, null);
        CustomUserDetails principal = new CustomUserDetails(user);

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(authenticationManager.authenticate(any()))
                .willReturn(new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
        given(jwtService.generateAccessToken(principal)).willReturn("access-token");
        given(refreshTokenService.issue(user)).willReturn("refresh-token");
        given(jwtService.getAccessTokenTtlSeconds()).willReturn(900L);

        authService.login(loginRequest("test@example.com", "Secret@123"));

        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(userRepository).save(user);
    }

    // ---------------------------------------------------------------- refresh / logout

    @Test
    void refresh_rotatesTokenAndReturnsNewPair() {

        User user = user(1L, true, 0, null);
        CustomUserDetails principal = new CustomUserDetails(user);

        given(refreshTokenService.rotate("old-refresh"))
                .willReturn(new RefreshTokenService.RotationResult(user, "new-refresh"));
        given(jwtService.generateAccessToken(any(CustomUserDetails.class))).willReturn("access-token");
        given(jwtService.getAccessTokenTtlSeconds()).willReturn(900L);

        LoginResponse response = authService.refresh("old-refresh");

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void logout_revokesTheSuppliedToken() {

        authService.logout("some-refresh");

        verify(refreshTokenService).revoke("some-refresh");
    }
}
