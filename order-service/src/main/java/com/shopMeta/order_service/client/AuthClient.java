package com.shopMeta.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.shopMeta.order_service.client.dto.AddressSnapshot;

/**
 * auth-service's internal address lookup (spec 7.3), used to snapshot the shipping address onto
 * the order at checkout.
 */
@FeignClient(name = "auth-service")
public interface AuthClient {

    @GetMapping("/api/v1/internal/users/{userId}/addresses/{addressId}")
    AddressSnapshot getAddress(@PathVariable("userId") Long userId,
            @PathVariable("addressId") Long addressId);
}
