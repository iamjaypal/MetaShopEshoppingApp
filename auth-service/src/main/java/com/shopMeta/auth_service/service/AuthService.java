package com.shopMeta.auth_service.service;

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
import com.shopMeta.auth_service.exception.InvalidTokenException;
import com.shopMeta.auth_service.repository.RoleRepository;
import com.shopMeta.auth_service.repository.UserRepository;
import com.shopMeta.auth_service.security.CustomUserDetails;
import com.shopMeta.auth_service.security.JwtService;
import com.shopMeta.auth_service.util.UserMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * Registration, login, refresh and logout (spec 7.3/7.4).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityProperties securityProperties;

    /**
     * Registers a new CUSTOMER (spec 7.3). Email is unique, case-insensitive; a duplicate
     * results in 409. The password must satisfy the policy in spec 7.4.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        Role customerRole = roleRepository
                .findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Role CUSTOMER not found"));

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .enabled(true)
                .emailVerified(false)
                .roles(Set.of(customerRole))
                .build();

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Lost a race against a concurrent registration with the same email.
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        // NOTE: UserRegisteredEvent is published in the Kafka phase (see D-025).

        return RegisterResponse.builder()
                .id(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .phone(savedUser.getPhone())
                .build();
    }

    /**
     * Authenticates a user and returns an access/refresh token pair (spec 7.3).
     * Locks the account for a while after {@code maxFailedLoginAttempts} failures (spec 7.4).
     */
    @Transactional(noRollbackFor = { InvalidCredentialsException.class, AccountLockedException.class })
    public LoginResponse login(LoginRequest request) {

        String email = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (isLocked(user)) {
            throw new AccountLockedException(
                    "Account is temporarily locked due to too many failed login attempts. Try again later.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (DisabledException ex) {
            throw new AccountDisabledException("Account is disabled");
        } catch (LockedException ex) {
            throw new AccountLockedException(
                    "Account is temporarily locked due to too many failed login attempts. Try again later.");
        } catch (BadCredentialsException ex) {
            registerFailedAttempt(user);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        resetFailedAttempts(user);

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        String refreshToken = refreshTokenService.issue(principal.getUser());

        return buildLoginResponse(principal, refreshToken);
    }

    /**
     * Rotates a refresh token and returns a fresh token pair (spec 7.3).
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public LoginResponse refresh(String refreshToken) {

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(refreshToken);

        return buildLoginResponse(new CustomUserDetails(result.user()), result.refreshToken());
    }

    /**
     * Revokes the supplied refresh token (spec 7.3).
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private LoginResponse buildLoginResponse(CustomUserDetails principal, String refreshToken) {

        return LoginResponse.builder()
                .accessToken(jwtService.generateAccessToken(principal))
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenTtlSeconds())
                .user(UserMapper.toSummary(principal.getUser()))
                .build();
    }

    private void registerFailedAttempt(User user) {

        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;

        if (attempts >= securityProperties.getMaxFailedLoginAttempts()) {
            user.setLockedUntil(LocalDateTime.now().plus(securityProperties.getLockDuration()));
            user.setFailedLoginAttempts(0);
        } else {
            user.setFailedLoginAttempts(attempts);
        }

        userRepository.save(user);
    }

    private void resetFailedAttempts(User user) {

        boolean hasAttempts = user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0;

        if (hasAttempts || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    private boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
