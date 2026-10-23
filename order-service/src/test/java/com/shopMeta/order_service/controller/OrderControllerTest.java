package com.shopMeta.order_service.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shopMeta.order_service.client.dto.UnavailableItemView;
import com.shopMeta.order_service.dto.OrderAddressResponse;
import com.shopMeta.order_service.dto.OrderItemResponse;
import com.shopMeta.order_service.dto.OrderPageResponse;
import com.shopMeta.order_service.dto.OrderResponse;
import com.shopMeta.order_service.entity.OrderStatus;
import com.shopMeta.order_service.entity.PaymentStatus;
import com.shopMeta.order_service.exception.CheckoutBlockedException;
import com.shopMeta.order_service.exception.DownstreamServiceException;
import com.shopMeta.order_service.exception.InsufficientStockException;
import com.shopMeta.order_service.exception.ResourceNotFoundException;
import com.shopMeta.order_service.security.AuthenticatedUser;
import com.shopMeta.order_service.service.OrderService;

/**
 * Web-layer tests: routing, validation errors and the mapping of service exceptions to HTTP
 * statuses. The filter chain is off (D-038); the principal is placed in the security context
 * directly so {@code @AuthenticationPrincipal} resolves without the JWT filter.
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(1L, "customer@metashop.com", List.of("CUSTOMER")),
                null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ fixtures

    static OrderResponse createdOrder() {

        return OrderResponse.builder()
                .id(100L)
                .userId(1L)
                .status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING)
                .items(List.of(OrderItemResponse.builder()
                        .id(1L).productId(50L).sku("SKU-50").name("Keyboard")
                        .unitPrice(new BigDecimal("100.00"))
                        .quantity(2)
                        .lineTotal(new BigDecimal("200.00"))
                        .build()))
                .subtotal(new BigDecimal("400.00"))
                .shippingFee(new BigDecimal("49.00"))
                .total(new BigDecimal("449.00"))
                .shippingAddress(OrderAddressResponse.builder()
                        .fullName("Jay Pal").phone("9876543210").line1("12 Palm Road")
                        .city("Surat").state("Gujarat").postalCode("395007").country("IN")
                        .build())
                .build();
    }

    // ------------------------------------------------------------------ checkout

    @Test
    void createOrder_returns201WithTheOrder() throws Exception {

        given(orderService.createOrder(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willReturn(createdOrder());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddressId\": 9}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.total").value(449.00))
                .andExpect(jsonPath("$.shippingAddress.city").value("Surat"));
    }

    @Test
    void createOrder_withMissingAddress_returnsValidationMap() throws Exception {

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.shippingAddressId").exists());
    }

    @Test
    void createOrder_withInsufficientStock_returns409WithTheLines() throws Exception {

        given(orderService.createOrder(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willThrow(new InsufficientStockException(
                        "Only 1 unit(s) of product 50 are available",
                        List.of(new UnavailableItemView(50L, 5, 1))));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddressId\": 9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data[0].productId").value(50))
                .andExpect(jsonPath("$.data[0].available").value(1));
    }

    @Test
    void createOrder_withBlockedCheckout_returns409WithTheIssues() throws Exception {

        given(orderService.createOrder(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willThrow(new CheckoutBlockedException("Your cart cannot be checked out right now",
                        List.of()));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddressId\": 9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createOrder_withDownstreamOutage_returns503() throws Exception {

        given(orderService.createOrder(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willThrow(new DownstreamServiceException(
                        "Could not reach inventory-service. Please try again shortly.", null));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddressId\": 9}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Could not reach inventory-service. Please try again shortly."));
    }

    // ------------------------------------------------------------------ reads

    @Test
    void listMyOrders_returnsThePage() throws Exception {

        given(orderService.listMyOrders(eq(1L), eq(0), eq(20)))
                .willReturn(OrderPageResponse.builder()
                        .content(List.of(createdOrder()))
                        .page(0).size(20).totalElements(1).totalPages(1).last(true)
                        .build());

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void getOrder_returnsTheOrder() throws Exception {

        given(orderService.getOrder(1L, 100L, false)).willReturn(createdOrder());

        mockMvc.perform(get("/api/v1/orders/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.items[0].productId").value(50));
    }

    @Test
    void getOrder_unknownOrForeign_returns404Envelope() throws Exception {

        given(orderService.getOrder(1L, 404L, false))
                .willThrow(new ResourceNotFoundException("Order 404 was not found"));

        mockMvc.perform(get("/api/v1/orders/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Order 404 was not found"));
    }

    // ------------------------------------------------------------------ cancel

    @Test
    void cancelOrder_returnsTheCancelledOrder() throws Exception {

        OrderResponse cancelled = createdOrder();
        cancelled.setStatus(OrderStatus.CANCELLED);
        given(orderService.cancelByOwner(1L, 100L)).willReturn(cancelled);

        mockMvc.perform(post("/api/v1/orders/100/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_ofAPaidOrder_returns422() throws Exception {

        given(orderService.cancelByOwner(1L, 100L))
                .willThrow(new com.shopMeta.order_service.exception.BusinessRuleException(
                        "Order 100 is CONFIRMED and can no longer be cancelled here "
                                + "— paid orders are refunded through payment-service"));

        mockMvc.perform(post("/api/v1/orders/100/cancel"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }
}
