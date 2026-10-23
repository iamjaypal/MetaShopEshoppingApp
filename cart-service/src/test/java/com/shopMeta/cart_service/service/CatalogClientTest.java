package com.shopMeta.cart_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopMeta.cart_service.client.InventoryClient;
import com.shopMeta.cart_service.client.ProductClient;
import com.shopMeta.cart_service.client.dto.AvailabilityBatchResponse;
import com.shopMeta.cart_service.client.dto.AvailabilityItem;
import com.shopMeta.cart_service.client.dto.AvailabilityLine;
import com.shopMeta.cart_service.client.dto.AvailabilityRequest;
import com.shopMeta.cart_service.client.dto.ProductBatchRequest;
import com.shopMeta.cart_service.client.dto.ProductSnapshot;
import com.shopMeta.cart_service.config.ResilienceConfig;
import com.shopMeta.cart_service.config.ResilienceProperties;
import com.shopMeta.cart_service.exception.DownstreamServiceException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

/**
 * Verifies the resilience decoration of the two downstream calls: the mapping of every failure to
 * {@link DownstreamServiceException}, the retry of transient errors before the breaker counts them,
 * and the keying of batch answers by product id.
 */
@ExtendWith(MockitoExtension.class)
class CatalogClientTest {

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    private CatalogClient catalogClient;

    @BeforeEach
    void setUp() {

        ResilienceProperties properties = new ResilienceProperties();
        properties.setRetryMaxAttempts(3);
        properties.setRetryWaitDuration(Duration.ofMillis(1));

        Retry retry = Retry.of("downstream-test",
                io.github.resilience4j.retry.RetryConfig.<String>custom()
                        .maxAttempts(properties.getRetryMaxAttempts())
                        .waitDuration(Duration.ofMillis(1))
                        .retryOnException(CatalogClientTest::isRetryable)
                        .build());

        CircuitBreaker productBreaker = CircuitBreaker.of("product-test",
                circuitBreakerConfig(properties));

        CircuitBreaker inventoryBreaker = CircuitBreaker.of("inventory-test",
                circuitBreakerConfig(properties));

        catalogClient = new CatalogClient(productClient, inventoryClient,
                retry, productBreaker, inventoryBreaker);
    }

    /** Same predicate as ResilienceConfig.isRetryable, kept local so tests stay hermetic. */
    private static boolean isRetryable(Throwable throwable) {
        return throwable instanceof IOException || throwable instanceof IllegalStateException;
    }

    private io.github.resilience4j.circuitbreaker.CircuitBreakerConfig circuitBreakerConfig(
            ResilienceProperties properties) {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .failureRateThreshold(properties.getFailureRateThreshold())
                .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                .build();
    }

    @Test
    @DisplayName("fetchProducts: keys the batch answer by product id")
    void fetchProducts_keysById() {

        givenBatchReturns(ProductSnapshot.builder()
                .id(50L).sku("SKU-50").name("Keyboard")
                .price(new java.math.BigDecimal("99.00")).active(true).build(),
                ProductSnapshot.builder()
                        .id(51L).sku("SKU-51").name("Mouse")
                        .price(new java.math.BigDecimal("199.00")).active(true).build());

        Map<Long, ProductSnapshot> products =
                catalogClient.fetchProducts(List.of(50L, 51L, 50L));

        assertThat(products).containsKeys(50L, 51L);
        assertThat(products.get(50L).getName()).isEqualTo("Keyboard");
    }

    @Test
    @DisplayName("fetchProducts: an empty id list makes no downstream call at all")
    void fetchProducts_skipsEmptyRequest() {

        Map<Long, ProductSnapshot> products = catalogClient.fetchProducts(List.of());

        assertThat(products).isEmpty();
        org.mockito.BDDMockito.then(productClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("fetchAvailability: keys the answer by product id and keeps the stock verdicts")
    void fetchAvailability_keysById() {

        org.mockito.BDDMockito.given(inventoryClient.checkAvailability(
                org.mockito.ArgumentMatchers.any(AvailabilityRequest.class)))
                .willReturn(new AvailabilityBatchResponse(List.of(
                        new AvailabilityItem(50L, "SKU-50", 2, 5, false, true)),
                        false));

        Map<Long, AvailabilityItem> stock =
                catalogClient.fetchAvailability(Map.of(50L, 5));

        assertThat(stock.get(50L).isSufficient()).isFalse();
        assertThat(stock.get(50L).getAvailable()).isEqualTo(2);
    }

    @Test
    @DisplayName("a failure that is not worth retrying fails fast as a 503 after one attempt")
    void nonRetryableFailure_becomesDownstreamException() {

        org.mockito.BDDMockito.given(productClient.getProductsBatch(anyRequest()))
                .willThrow(new NullPointerException("unexpected"));

        assertThatThrownBy(() -> catalogClient.fetchProducts(List.of(1L)))
                .isInstanceOf(DownstreamServiceException.class);

        org.mockito.BDDMockito.then(productClient)
                .should(org.mockito.Mockito.times(1)).getProductsBatch(anyRequest());
    }

    @Test
    @DisplayName("a transient failure is retried before it is reported — a blip does not 503")
    void transientFailure_isRetried() {

        // IllegalStateException is retryable by the shared policy; a checked exception cannot be
        // stubbed on a method that does not declare it.
        org.mockito.BDDMockito.given(productClient.getProductsBatch(anyRequest()))
                .willThrow(new IllegalStateException("connection reset"))
                .willReturn(List.of(ProductSnapshot.builder().id(1L).build()));

        Map<Long, ProductSnapshot> products = catalogClient.fetchProducts(List.of(1L));

        assertThat(products).containsKey(1L);
        org.mockito.BDDMockito.then(productClient).should(org.mockito.Mockito.times(2))
                .getProductsBatch(anyRequest());
    }

    private ProductBatchRequest anyRequest() {
        return org.mockito.ArgumentMatchers.any(ProductBatchRequest.class);
    }

    private void givenBatchReturns(ProductSnapshot... products) {
        org.mockito.BDDMockito.given(productClient.getProductsBatch(anyRequest()))
                .willReturn(List.of(products));
    }
}
