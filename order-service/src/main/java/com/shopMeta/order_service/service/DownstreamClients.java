package com.shopMeta.order_service.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopMeta.order_service.client.AuthClient;
import com.shopMeta.order_service.client.CartInternalClient;
import com.shopMeta.order_service.client.CartSessionClient;
import com.shopMeta.order_service.client.InventoryClient;
import com.shopMeta.order_service.client.ProductClient;
import com.shopMeta.order_service.client.dto.AddressSnapshot;
import com.shopMeta.order_service.client.dto.CartValidationView;
import com.shopMeta.order_service.client.dto.CartView;
import com.shopMeta.order_service.client.dto.OrderReserveRequest;
import com.shopMeta.order_service.client.dto.OrderReservationRequest;
import com.shopMeta.order_service.client.dto.ProductBatchRequest;
import com.shopMeta.order_service.client.dto.ProductSnapshot;
import com.shopMeta.order_service.client.dto.ReservationResponse;
import com.shopMeta.order_service.client.dto.UnavailableItemView;
import com.shopMeta.order_service.config.ResilienceConfig;
import com.shopMeta.order_service.exception.BusinessRuleException;
import com.shopMeta.order_service.exception.DownstreamServiceException;
import com.shopMeta.order_service.exception.InsufficientStockException;
import com.shopMeta.order_service.exception.ResourceNotFoundException;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

/**
 * The single place where order-service talks to the rest of the platform (spec 11).
 *
 * <p>Every call goes through the shared retry policy and the per-service circuit breaker
 * (spec 5.8). Unreachable, timed-out or breaker-open outcomes become a
 * {@link DownstreamServiceException} — a clear 503, never an order built on guesses.
 *
 * <p>Two business answers are unwrapped from their Feign status and rethrown typed, because they
 * are <em>answers</em>, not failures:
 *
 * <ul>
 *   <li>reserve → 409 becomes {@link InsufficientStockException} with the offending lines parsed
 *       from inventory-service's response body.</li>
 *   <li>address lookup → 404 becomes {@link ResourceNotFoundException}.</li>
 * </ul>
 */
@Service
public class DownstreamClients {

    private static final Logger log = LoggerFactory.getLogger(DownstreamClients.class);

    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final AuthClient authClient;
    private final CartSessionClient cartSessionClient;
    private final CartInternalClient cartInternalClient;
    private final ObjectMapper objectMapper;

    private final Retry retry;
    private final CircuitBreaker productCircuitBreaker;
    private final CircuitBreaker inventoryCircuitBreaker;
    private final CircuitBreaker authCircuitBreaker;
    private final CircuitBreaker cartCircuitBreaker;

    public DownstreamClients(
            ProductClient productClient,
            InventoryClient inventoryClient,
            AuthClient authClient,
            CartSessionClient cartSessionClient,
            CartInternalClient cartInternalClient,
            ObjectMapper objectMapper,
            Retry downstreamRetry,
            @Qualifier("productServiceCircuitBreaker") CircuitBreaker productCircuitBreaker,
            @Qualifier("inventoryServiceCircuitBreaker") CircuitBreaker inventoryCircuitBreaker,
            @Qualifier("authServiceCircuitBreaker") CircuitBreaker authCircuitBreaker,
            @Qualifier("cartServiceCircuitBreaker") CircuitBreaker cartCircuitBreaker) {

        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.authClient = authClient;
        this.cartSessionClient = cartSessionClient;
        this.cartInternalClient = cartInternalClient;
        this.objectMapper = objectMapper;
        this.retry = downstreamRetry;
        this.productCircuitBreaker = productCircuitBreaker;
        this.inventoryCircuitBreaker = inventoryCircuitBreaker;
        this.authCircuitBreaker = authCircuitBreaker;
        this.cartCircuitBreaker = cartCircuitBreaker;
    }

    // ------------------------------------------------------------------ product-service

    /**
     * Live catalogue data for the given ids, keyed by product id. Ids the catalogue does not know
     * about are simply absent from the result — the caller decides whether that is fatal
     * (checkout) or not.
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

    // ------------------------------------------------------------------ auth-service

    /**
     * The shipping address to copy onto the order. 404 (unknown id, or an address that belongs to
     * a different user) becomes {@link ResourceNotFoundException}.
     */
    public AddressSnapshot fetchAddress(Long userId, Long addressId) {

        try {
            return call(authCircuitBreaker,
                    () -> authClient.getAddress(userId, addressId),
                    ResilienceConfig.AUTH_SERVICE);

        } catch (DownstreamServiceException ex) {
            if (causeOf(ex) instanceof FeignException.NotFound) {
                throw new ResourceNotFoundException(
                        "Shipping address " + addressId + " was not found");
            }
            throw ex;
        }
    }

    // ------------------------------------------------------------------ cart-service

    /**
     * The buyer's cart, read through their own forwarded JWT (D-061) — the cart belongs to the
     * buyer's session, so there is no internal read endpoint to hide behind.
     */
    public CartView loadCart() {
        return call(cartCircuitBreaker, cartSessionClient::getCart, ResilienceConfig.CART_SERVICE);
    }

    /** cart-service's pre-checkout validation, again under the buyer's JWT. */
    public CartValidationView validateCart() {
        return call(cartCircuitBreaker, cartSessionClient::validateCart,
                ResilienceConfig.CART_SERVICE);
    }

    /** Empties the buyer's cart after the payment succeeded (idempotent). */
    public void clearCart(Long userId) {

        call(cartCircuitBreaker, () -> {
            cartInternalClient.clearCart(userId);
            return null;
        }, ResilienceConfig.CART_SERVICE);
    }

    // ------------------------------------------------------------------ inventory-service

    /**
     * All-or-nothing stock hold for the order (idempotent per {@code orderId}, D-053).
     *
     * <p>A 409 is a normal answer, not a service failure: the body is parsed into an
     * {@link InsufficientStockException} carrying the unavailable lines (spec 19).
     */
    public ReservationResponse reserveStock(Long orderId, OrderReserveRequest request) {

        try {
            return call(inventoryCircuitBreaker,
                    () -> inventoryClient.reserve(request),
                    ResilienceConfig.INVENTORY_SERVICE);

        } catch (DownstreamServiceException ex) {
            if (causeOf(ex) instanceof FeignException.Conflict conflict) {
                throw insufficientStock(conflict);
            }
            throw ex;
        }
    }

    /** Turns the order's holds into sales once the payment succeeded (idempotent). */
    public ReservationResponse confirmStock(Long orderId) {

        try {
            return call(inventoryCircuitBreaker,
                    () -> inventoryClient.confirm(new OrderReservationRequest(orderId)),
                    ResilienceConfig.INVENTORY_SERVICE);

        } catch (DownstreamServiceException ex) {
            if (causeOf(ex) instanceof FeignException feign && feign.status() == 422) {
                // The reservation expired or was released while the payment was in flight.
                throw new BusinessRuleException(envelopeMessage(feign,
                        "The reserved stock for order " + orderId
                                + " expired and the order cannot be confirmed"));
            }
            throw ex;
        }
    }

    /** Gives the order's holds back to available stock after a failure/cancel (idempotent). */
    public ReservationResponse releaseStock(Long orderId) {

        try {
            return call(inventoryCircuitBreaker,
                    () -> inventoryClient.release(new OrderReservationRequest(orderId)),
                    ResilienceConfig.INVENTORY_SERVICE);

        } catch (DownstreamServiceException ex) {
            if (causeOf(ex) instanceof FeignException feign && feign.status() == 422) {
                throw new BusinessRuleException(envelopeMessage(feign,
                        "The stock for order " + orderId
                                + " is already sold and cannot be released"));
            }
            throw ex;
        }
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * Runs a downstream call through {@code Retry(CircuitBreaker(call))}.
     *
     * <p>The retry is outermost on purpose: a transient failure is retried before the breaker
     * counts it, so a single blip cannot open the circuit. Once the breaker is open the call fails
     * fast instead of adding load to a service that is already struggling.
     *
     * <p>4xx answers from the downstream service are <strong>not</strong> retried (the request is
     * wrong, not the service) and pass through as the cause of the
     * {@link DownstreamServiceException} for the typed methods above to unwrap.
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

    private static Throwable causeOf(DownstreamServiceException ex) {
        return ex.getCause() == null ? ex : ex.getCause();
    }

    /** Parses inventory-service's 409 envelope into the typed exception. */
    private InsufficientStockException insufficientStock(FeignException conflict) {

        String fallback = "Some items in your order are out of stock";

        try {
            JsonNode root = objectMapper.readTree(conflict.contentUTF8());
            String message = root.path("message").asText(fallback);

            List<UnavailableItemView> items = new java.util.ArrayList<>();
            JsonNode data = root.path("data");
            if (data.isArray()) {
                data.forEach(node -> items.add(new UnavailableItemView(
                        node.path("productId").asLong(),
                        node.path("requested").asInt(),
                        node.path("available").asInt())));
            }

            return new InsufficientStockException(message, items);

        } catch (Exception parseFailure) {
            log.warn("Could not parse the 409 body from inventory-service: {}", parseFailure.getMessage());
            return new InsufficientStockException(fallback, java.util.List.of());
        }
    }

    /** Reads the {@code message} field of an ApiResponse error envelope, with a fallback. */
    private String envelopeMessage(FeignException feignException, String fallback) {

        try {
            JsonNode root = objectMapper.readTree(feignException.contentUTF8());
            String message = root.path("message").asText("");
            return message.isBlank() ? fallback : message;

        } catch (Exception parseFailure) {
            return fallback;
        }
    }
}
