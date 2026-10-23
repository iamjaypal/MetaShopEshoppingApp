package com.shopMeta.auth_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.auth_service.dto.AddressResponse;
import com.shopMeta.auth_service.service.AddressService;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Internal, service-to-service endpoint (spec 7.3). Used by order-service to snapshot the
 * shipping address. Returns plain JSON (no envelope) for easy Feign consumption.
 *
 * <p>Not routed publicly by the Gateway; to be protected by {@code X-Internal-Token}
 * (spec 5.4) in the security-hardening phase.
 */
@Tag(name = "Users (internal)", description = "Service-to-service address lookup")
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final AddressService addressService;

    @Operation(summary = "Address snapshot for order-service")
    @GetMapping("/{userId}/addresses/{addressId}")
    public ResponseEntity<AddressResponse> getAddress(
            @PathVariable Long userId,
            @PathVariable Long addressId) {

        return ResponseEntity.ok(addressService.get(userId, addressId));
    }
}
