package com.shopMeta.auth_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.auth_service.dto.AddressRequest;
import com.shopMeta.auth_service.dto.AddressResponse;
import com.shopMeta.auth_service.dto.ApiResponse;
import com.shopMeta.auth_service.security.CustomUserDetails;
import com.shopMeta.auth_service.service.AddressService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The authenticated user's address book (spec 7.3). Owner-only; requires a JWT.
 */
@Tag(name = "Addresses (me)", description = "Own address book (max 10, one default)")
@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "Add an address")
    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody AddressRequest request) {

        AddressResponse address = addressService.create(principal.getUser().getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<AddressResponse>builder()
                .success(true)
                .message("Address added successfully")
                .data(address)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> listAddresses(
            @AuthenticationPrincipal CustomUserDetails principal) {

        List<AddressResponse> addresses = addressService.list(principal.getUser().getId());

        return ResponseEntity.ok(ApiResponse.<List<AddressResponse>>builder()
                .success(true)
                .message("Addresses fetched successfully")
                .data(addresses)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {

        AddressResponse address = addressService.get(principal.getUser().getId(), id);

        return ResponseEntity.ok(ApiResponse.<AddressResponse>builder()
                .success(true)
                .message("Address fetched successfully")
                .data(address)
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {

        AddressResponse address = addressService.update(principal.getUser().getId(), id, request);

        return ResponseEntity.ok(ApiResponse.<AddressResponse>builder()
                .success(true)
                .message("Address updated successfully")
                .data(address)
                .build());
    }

    @Operation(summary = "Delete an address (the default is re-assigned)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {

        addressService.delete(principal.getUser().getId(), id);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Make an address the default")
    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {

        AddressResponse address = addressService.setDefault(principal.getUser().getId(), id);

        return ResponseEntity.ok(ApiResponse.<AddressResponse>builder()
                .success(true)
                .message("Default address updated successfully")
                .data(address)
                .build());
    }
}
