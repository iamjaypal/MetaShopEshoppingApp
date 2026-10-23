package com.shopMeta.auth_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.auth_service.dto.AdminUserResponse;
import com.shopMeta.auth_service.dto.ApiResponse;
import com.shopMeta.auth_service.dto.PageResponse;
import com.shopMeta.auth_service.dto.UpdateUserStatusRequest;
import com.shopMeta.auth_service.security.CustomUserDetails;
import com.shopMeta.auth_service.service.AdminUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Admin user management (spec 7.3). Requires the ADMIN role.
 */
@Tag(name = "Users (admin)", description = "Admin user management")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "List users (filter by email/role/enabled, paginated)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> listUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<AdminUserResponse> users =
                adminUserService.listUsers(email, role, enabled, page, size);

        return ResponseEntity.ok(ApiResponse.<PageResponse<AdminUserResponse>>builder()
                .success(true)
                .message("Users fetched successfully")
                .data(users)
                .build());
    }

    @Operation(summary = "Enable or disable a user")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateStatus(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {

        AdminUserResponse user =
                adminUserService.updateStatus(principal.getUser().getId(), id, request.getEnabled());

        return ResponseEntity.ok(ApiResponse.<AdminUserResponse>builder()
                .success(true)
                .message(request.getEnabled() ? "User enabled successfully" : "User disabled successfully")
                .data(user)
                .build());
    }
}
