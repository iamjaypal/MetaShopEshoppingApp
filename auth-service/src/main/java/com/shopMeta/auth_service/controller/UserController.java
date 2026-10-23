package com.shopMeta.auth_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.auth_service.dto.ApiResponse;
import com.shopMeta.auth_service.dto.ChangePasswordRequest;
import com.shopMeta.auth_service.dto.UpdateProfileRequest;
import com.shopMeta.auth_service.dto.UserSummary;
import com.shopMeta.auth_service.security.CustomUserDetails;
import com.shopMeta.auth_service.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The authenticated user's own profile (spec 7.3). All endpoints require a JWT.
 */
@Tag(name = "Users (me)", description = "Own profile and password")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get my profile")
    @GetMapping
    public ResponseEntity<ApiResponse<UserSummary>> getProfile(
            @AuthenticationPrincipal CustomUserDetails principal) {

        UserSummary user = userService.getProfile(principal.getUser().getId());

        return ResponseEntity.ok(ApiResponse.<UserSummary>builder()
                .success(true)
                .message("Profile fetched successfully")
                .data(user)
                .build());
    }

    @Operation(summary = "Update my name/phone")
    @PutMapping
    public ResponseEntity<ApiResponse<UserSummary>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserSummary user = userService.updateProfile(principal.getUser().getId(), request);

        return ResponseEntity.ok(ApiResponse.<UserSummary>builder()
                .success(true)
                .message("Profile updated successfully")
                .data(user)
                .build());
    }

    @Operation(summary = "Change my password")
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(principal.getUser().getId(), request);

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<Void>builder()
                .success(true)
                .message("Password changed successfully")
                .data(null)
                .build());
    }
}
