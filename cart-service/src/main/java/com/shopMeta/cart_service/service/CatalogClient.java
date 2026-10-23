package com.shopMeta.cart_service.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.shopMeta.cart_service.client.InventoryClient;
import com.shopMeta.cart_service.client.ProductClient;
import com.shopMeta.cart_service.client.dto.AvailabilityBatchResponse;
import com.shopMeta.cart_service.client.dto.AvailabilityItem;
import com.shopMeta.cart_service.client.dto.AvailabilityLine;
import com.shopMeta.cart_service.client.dto.AvailabilityRequest;
import com.shopMeta.cart_service.client.dto.ProductBatchRequest;
import com.shopMeta.cart_service.client.dto.ProductSnapshot;
import com.shopMeta.cart_service.config.ResilienceConfig;
import com.shopMeta.cart_service.exception.DownstreamServiceException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The single place where cart-service talks to the rest of the platform (spec 10.2).
 *
 * <p>Every call goes through the shared retry policy and the per-service circuit breaker
 * (spec 5.8). When a dependency is unreachable the call is turned into a
 * {@link DownstreamServiceException}, which the API surfaces as a clear 503 rather than a stack
 * trace or — worse — a cart rendered with invented prices (spec 19).
 */
@Service
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    private final Retry retry;
    private final CircuitBreaker productCircuitBreaker;
    private final CircuitBreaker inventoryCircuitBreaker;

    public CatalogClient(
            ProductClient productClient,
            InventoryClient inventoryClient,
            Retry downstreamRetry,
            @Qualifier("productServiceCircuitBreaker") CircuitBreaker productCircuitBreaker,
            @Qualifier("inventoryServiceCircuitBreaker") CircuitBreaker inventoryCircuitBreaker) {

        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.retry = downstreamRetry;
        this.productCircuitBreaker = productCircuitBreaker;
        this.inventoryCircuitBreaker = inventoryCircuitBreaker;
    }

    /**
     * Live product data (name, image, price, active) for the given ids, keyed by product id.
     * Ids the catalogue does not know about are simply absent from the result.
     */
    public Map<Long, ProductSnapshot> fetchProducts(Collection<Long> productIds) {

        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = productIds.stream().distinct().toList();

        List<ProductSnapshot> products = call(productCircuitBreaker,
                () -> productClient.getProductsBatch(new ProductBatchRequest(ids)),
                ResilienceConfig.PRODUCT_SERVICE);

        Map<Long, ProductSnapshot> byProductId = new LinkedHashMap<>();
        if (products != null) {
            products.forEach(product -> byProductId.put(product.getId(), product));
        }
        return byProductId;
    }

    /**
     * Exact availability for the given products, keyed by product id. The map value is the quantity
     * the caller needs, so the answer already says whether that many can be bought.
     */
    public Map<Long, AvailabilityItem> fetchAvailability(Map<Long, Integer> quantityByProductId) {

        if (quantityByProductId == null || quantityByProductId.isEmpty()) {
            return Map.of();
        }

        List<AvailabilityLine> lines = quantityByProductId.entrySet().stream()
                .map(entry -> new AvailabilityLine(entry.getKey(), entry.getValue()))
                .toList();

        AvailabilityBatchResponse response = call(inventoryCircuitBreaker,
                () -> inventoryClient.checkAvailability(new AvailabilityRequest(lines)),
                ResilienceConfig.INVENTORY_SERVICE);

        Map<Long, AvailabilityItem> byProductId = new LinkedHashMap<>();
        if (response != null && response.getItems() != null) {
            response.getItems().forEach(item -> byProductId.put(item.getProductId(), item));
        }
        return byProductId;
    }

    /**
     * Runs a downstream read through {@code Retry(CircuitBreaker(call))}.
     *
     * <p>The retry is outermost on purpose: a transient failure is retried before the breaker counts
     * it, so a single blip cannot open the circuit. Once the breaker is open the call fails fast
     * instead of adding load to a service that is already struggling.
     */
    private <T> T call(CircuitBreaker circuitBreaker, Supplier<T> request, String serviceName) {

        Supplier<T> guarded = Retry.decorateSupplier(
                retry, CircuitBreaker.decorateSupplier(circuitBreaker, request));

        try {
            return guarded.get();
        } catch (CallNotPermittedException ex) {
            log.warn("{} circuit breaker is open; failing fast", serviceName);
            throw new DownstreamServiceException(
                    serviceName + " is temporarily unavailable. Please try again shortly.", ex);
        } catch (RuntimeException ex) {
            // FeignException (bad status), RetryableException (connection refused/timeout) and
            // LoadBalancer's "no instances" all land here once the retries are exhausted.
            log.error("{} call failed: {}", serviceName, ex.getMessage());
            throw new DownstreamServiceException(
                    "Could not reach " + serviceName + ". Please try again shortly.", ex);
        }
    }
}
