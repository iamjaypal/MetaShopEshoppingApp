package com.shopMeta.auth_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.auth_service.dto.ApiResponse;
import com.shopMeta.auth_service.dto.ForgotPasswordRequest;
import com.shopMeta.auth_service.dto.LoginRequest;
import com.shopMeta.auth_service.dto.LoginResponse;
import com.shopMeta.auth_service.dto.RefreshTokenRequest;
import com.shopMeta.auth_service.dto.RegisterRequest;
import com.shopMeta.auth_service.dto.RegisterResponse;
import com.shopMeta.auth_service.dto.ResetPasswordRequest;
import com.shopMeta.auth_service.service.AuthService;
import com.shopMeta.auth_service.service.PasswordResetService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Authentication endpoints (spec 7.3). Register/login/refresh are public; logout requires a JWT.
 */
@Tag(name = "Auth", description = "Registration, login, token refresh/logout and password reset")
@RestController
@RequestMapping("/api/v1/auth")
@AllArgsConstructor
public class AuthController {

    private AuthService authservice;

    private PasswordResetService passwordResetService;

    @Operation(summary = "Register a new CUSTOMER")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> createUser(@Valid @RequestBody RegisterRequest request) {

        RegisterResponse registerResponse = authservice.register(request);

        ApiResponse<RegisterResponse> response = ApiResponse.<RegisterResponse>builder()
                .success(true)
                .message("User Registered Successfully")
                .data(registerResponse).build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Login and obtain an access/refresh token pair")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse loginResponse = authservice.login(request);

        ApiResponse<LoginResponse> response = ApiResponse.<LoginResponse>builder()
                .success(true)
                .message("Login Successful")
                .data(loginResponse)
                .build();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @Operation(summary = "Rotate the refresh token and return a new token pair")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        LoginResponse loginResponse = authservice.refresh(request.getRefreshToken());

        ApiResponse<LoginResponse> response = ApiResponse.<LoginResponse>builder()
                .success(true)
                .message("Token refreshed successfully")
                .data(loginResponse)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Revoke the supplied refresh token (requires a JWT)")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request) {

        authservice.logout(request.getRefreshToken());

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Logged out successfully")
                .data(null)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Request a password-reset link")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        passwordResetService.requestReset(request);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("If the email is registered, a password reset link has been sent")
                .data(null)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Reset the password with a reset token")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.resetPassword(request);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Password has been reset successfully")
                .data(null)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

}
