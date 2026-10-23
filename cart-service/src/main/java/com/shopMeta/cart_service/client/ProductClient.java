package com.shopMeta.cart_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.shopMeta.cart_service.client.dto.ProductBatchRequest;
import com.shopMeta.cart_service.client.dto.ProductSnapshot;

import java.util.List;

/**
 * product-service's internal batch lookup (spec 8.3).
 *
 * <p>{@code name} is the Eureka application id, so Spring Cloud LoadBalancer picks a live instance.
 * The shared {@code X-Internal-Token} header is attached by
 * {@link com.shopMeta.cart_service.config.FeignInternalTokenConfig}.
 */
@FeignClient(name = "product-service")
public interface ProductClient {

    @PostMapping("/api/v1/internal/products/batch")
    List<ProductSnapshot> getProductsBatch(@RequestBody ProductBatchRequest request);
}
