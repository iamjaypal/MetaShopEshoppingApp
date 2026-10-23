package com.shopMeta.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.shopMeta.order_service.client.dto.CartView;
import com.shopMeta.order_service.client.dto.CartValidationView;
import com.shopMeta.order_service.config.FeignUserTokenConfig;

/**
 * cart-service's <strong>public</strong> cart API, called with the buyer's own JWT (D-061).
 *
 * <p>The cart belongs to the user's session, so there is deliberately no "read another user's cart"
 * internal endpoint. The token the caller used on the gateway is forwarded by
 * {@link FeignUserTokenConfig} — the same client is the buyer as far as cart-service is concerned.
 */
@FeignClient(name = "cart-service", contextId = "cartSessionClient",
        configuration = FeignUserTokenConfig.class)
public interface CartSessionClient {

    @GetMapping("/api/v1/cart")
    CartView getCart();

    @PostMapping("/api/v1/cart/validate")
    CartValidationView validateCart();
}
