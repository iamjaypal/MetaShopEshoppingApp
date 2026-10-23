package com.shopMeta.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * cart-service's one internal operation (spec 10.3): empty a user's cart once their order is
 * confirmed. Idempotent — a replayed confirmation is harmless.
 *
 * <p>Carries {@code X-Internal-Token} via the global
 * {@link com.shopMeta.order_service.config.FeignInternalTokenConfig}. Kept separate from
 * {@link CartSessionClient} because the two authenticate differently (internal token vs the
 * buyer's forwarded JWT).
 */
@FeignClient(name = "cart-service", contextId = "cartInternalClient")
public interface CartInternalClient {

    @DeleteMapping("/api/v1/internal/cart/{userId}")
    void clearCart(@PathVariable("userId") Long userId);
}
