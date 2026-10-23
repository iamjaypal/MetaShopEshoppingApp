package com.shopMeta.order_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopMeta.order_service.client.AuthClient;
import com.shopMeta.order_service.client.CartInternalClient;
import com.shopMeta.order_service.client.CartSessionClient;
import com.shopMeta.order_service.client.InventoryClient;
import com.shopMeta.order_service.client.ProductClient;
import com.shopMeta.order_service.client.dto.OrderReserveRequest;
import com.shopMeta.order_service.client.dto.ProductBatchRequest;
import com.shopMeta.order_service.client.dto.ProductSnapshot;
import com.shopMeta.order_service.client.dto.ReservationResponse;
import com.shopMeta.order_service.config.ResilienceProperties;
import com.shopMeta.order_service.exception.DownstreamServiceException;
import com.shopMeta.order_service.exception.InsufficientStockException;
import com.shopMeta.order_service.exception.ResourceNotFoundException;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

/**
 * Verifies the resilience decoration of the downstream calls: every unreachable/timeout/open-breaker
 * outcome becomes {@link DownstreamServiceException} (503), transient failures are retried before
 * they are reported, and the business answers hidden inside Feign statuses are unwrapped —
 * the reserve 409 into {@link InsufficientStockException} with the parsed lines, the address 404
 * into {@link ResourceNotFoundException}.
 */
@ExtendWith(MockitoExtension.class)
class DownstreamClientsTest {

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private AuthClient authClient;

    @Mock
    private CartSessionClient cartSessionClient;

    @Mock
    private CartInternalClient cartInternalClient;

    private DownstreamClients downstreamClients;

    @BeforeEach
    void setUp() {

        ResilienceProperties properties = new ResilienceProperties();
        properties.setRetryMaxAttempts(3);
        properties.setRetryWaitDuration(Duration.ofMillis(1));

        Retry retry = Retry.of("downstream-test",
                io.github.resilience4j.retry.RetryConfig.<String>custom()
                        .maxAttempts(properties.getRetryMaxAttempts())
                        .waitDuration(Duration.ofMillis(1))
                        .retryOnException(DownstreamClientsTest::isRetryable)
                        .build());

        CircuitBreaker breaker = CircuitBreaker.of("test",
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowSize(properties.getSlidingWindowSize())
                        .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                        .failureRateThreshold(properties.getFailureRateThreshold())
                        .build());

        downstreamClients = new DownstreamClients(productClient, inventoryClient, authClient,
                cartSessionClient, cartInternalClient, new ObjectMapper(), retry,
                breaker, breaker, breaker, breaker);
    }

    /** Same predicate as ResilienceConfig.isRetryable, kept local so tests stay hermetic. */
    private static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof FeignException feignException) {
            return feignException.status() <= 0 || feignException.status() >= 500;
        }
        return throwable instanceof IllegalStateException;
    }

    private static FeignException feignError(Class<? extends FeignException> type, String body)
            throws Exception {

        Request request = Request.create(Request.HttpMethod.POST,
                "http://inventory-service/api/v1/internal/inventory/reserve",
                Map.of(), body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8, new RequestTemplate());

        return type.getDeclaredConstructor(String.class, Request.class, byte[].class, Map.class)
                .newInstance("downstream answered " + type.getSimpleName(), request,
                        body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                        Map.of());
    }

    // ------------------------------------------------------------------ product-service

    @Test
    @DisplayName("fetchProducts: keys the batch answer by product id, de-duplicating the request")
    void fetchProducts_keysById() {

        given(productClient.getProductsBatch(any(ProductBatchRequest.class)))
                .willReturn(List.of(
                        ProductSnapshot.builder().id(50L).name("Keyboard").build(),
                        ProductSnapshot.builder().id(51L).name("Mouse").build()));

        Map<Long, ProductSnapshot> products = downstreamClients.fetchProducts(List.of(50L, 51L, 50L));

        assertThat(products).containsKeys(50L, 51L);
        assertThat(products.get(50L).getName()).isEqualTo("Keyboard");
    }

    @Test
    @DisplayName("fetchProducts: an empty id list makes no downstream call at all")
    void fetchProducts_skipsEmptyRequest() {

        assertThat(downstreamClients.fetchProducts(List.of())).isEmpty();

        then(productClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("a transient failure is retried before it is reported — a blip does not 503")
    void transientFailure_isRetried() {

        // IllegalStateException is retryable by the shared policy; a checked exception cannot be
        // stubbed on a method that does not declare it.
        given(productClient.getProductsBatch(any(ProductBatchRequest.class)))
                .willThrow(new IllegalStateException("connection reset"))
                .willReturn(List.of(ProductSnapshot.builder().id(1L).build()));

        assertThat(downstreamClients.fetchProducts(List.of(1L))).containsKey(1L);

        then(productClient).should(times(2)).getProductsBatch(any(ProductBatchRequest.class));
    }

    @Test
    @DisplayName("a failure that is not worth retrying is reported after one attempt")
    void nonRetryableFailure_singleAttempt() {

        given(productClient.getProductsBatch(any(ProductBatchRequest.class)))
                .willThrow(new NullPointerException("unexpected"));

        assertThatThrownBy(() -> downstreamClients.fetchProducts(List.of(1L)))
                .isInstanceOf(DownstreamServiceException.class);

        then(productClient).should(times(1)).getProductsBatch(any(ProductBatchRequest.class));
    }

    // ------------------------------------------------------------------ inventory-service

    @Test
    @DisplayName("reserveStock: the 409 envelope is unwrapped into InsufficientStockException with the lines")
    void reserveStock_parsesTheConflictBody() throws Exception {

        given(inventoryClient.reserve(any(OrderReserveRequest.class)))
                .willThrow(feignError(FeignException.Conflict.class, """
                        {"success":false,"message":"Only 1 unit(s) of product 50 are available",\
                        "data":[{"productId":50,"requested":5,"available":1}]}"""));

        assertThatThrownBy(() -> downstreamClients.reserveStock(100L, anyReserveRequest()))
                .isInstanceOfSatisfying(InsufficientStockException.class, ex -> {
                    assertThat(ex.getMessage())
                            .isEqualTo("Only 1 unit(s) of product 50 are available");
                    assertThat(ex.getUnavailableItems()).hasSize(1);
                    assertThat(ex.getUnavailableItems().get(0).getProductId()).isEqualTo(50L);
                    assertThat(ex.getUnavailableItems().get(0).getRequested()).isEqualTo(5);
                    assertThat(ex.getUnavailableItems().get(0).getAvailable()).isEqualTo(1);
                });

        // A 409 is an answer, not an outage — it must not be retried.
        then(inventoryClient).should(times(1)).reserve(any(OrderReserveRequest.class));
    }

    @Test
    @DisplayName("reserveStock: an unparseable 409 body still becomes InsufficientStockException")
    void reserveStock_toleratesGarbageBody() throws Exception {

        given(inventoryClient.reserve(any(OrderReserveRequest.class)))
                .willThrow(feignError(FeignException.Conflict.class, "<html>gateway</html>"));

        assertThatThrownBy(() -> downstreamClients.reserveStock(100L, anyReserveRequest()))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("out of stock");
    }

    @Test
    @DisplayName("reserveStock: an inventory outage is a 503 after the retries")
    void reserveStock_outage() {

        given(inventoryClient.reserve(any(OrderReserveRequest.class)))
                .willThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> downstreamClients.reserveStock(100L, anyReserveRequest()))
                .isInstanceOf(DownstreamServiceException.class)
                .hasMessageContaining("inventory-service");

        then(inventoryClient).should(times(3)).reserve(any(OrderReserveRequest.class));
    }

    @Test
    @DisplayName("confirmStock: the 422 of an expired reservation becomes a BusinessRuleException")
    void confirmStock_expiredReservation() throws Exception {

        given(inventoryClient.confirm(any()))
                .willThrow(feignError(FeignException.UnprocessableEntity.class, """
                        {"success":false,"message":"Reservation for product 50 was expired \
                        and can no longer be confirmed"}"""));

        assertThatThrownBy(() -> downstreamClients.confirmStock(100L))
                .isInstanceOf(com.shopMeta.order_service.exception.BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    // ------------------------------------------------------------------ auth-service

    @Test
    @DisplayName("fetchAddress: a 404 (unknown id or not yours) becomes ResourceNotFoundException")
    void fetchAddress_maps404() throws Exception {

        given(authClient.getAddress(anyLong(), anyLong()))
                .willThrow(feignError(FeignException.NotFound.class,
                        "{\"success\":false,\"message\":\"Address not found\"}"));

        assertThatThrownBy(() -> downstreamClients.fetchAddress(1L, 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("9");
    }

    @Test
    @DisplayName("fetchAddress: an auth-service outage is a 503")
    void fetchAddress_outage() {

        given(authClient.getAddress(anyLong(), anyLong()))
                .willThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> downstreamClients.fetchAddress(1L, 9L))
                .isInstanceOf(DownstreamServiceException.class)
                .hasMessageContaining("auth-service");
    }

    // ------------------------------------------------------------------ cart-service

    @Test
    @DisplayName("clearCart: a cart-service outage is a 503 (the caller decides whether to care)")
    void clearCart_outage() {

        doThrow(new IllegalStateException("connection refused"))
                .when(cartInternalClient).clearCart(anyLong());

        assertThatThrownBy(() -> downstreamClients.clearCart(1L))
                .isInstanceOf(DownstreamServiceException.class);
    }

    @Test
    @DisplayName("loadCart: the cart read goes through the same retry/breaker pipeline")
    void loadCart_outage() {

        given(cartSessionClient.getCart()).willThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> downstreamClients.loadCart())
                .isInstanceOf(DownstreamServiceException.class);

        then(cartSessionClient).should(times(3)).getCart();
    }

    private static OrderReserveRequest anyReserveRequest() {
        return OrderReserveRequest.of(100L, Map.of(50L, 5));
    }
}
