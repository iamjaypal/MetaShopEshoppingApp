package com.shopMeta.cart_service.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.shopMeta.cart_service.dto.CartResponse;
import com.shopMeta.cart_service.dto.CartValidationResponse;
import com.shopMeta.cart_service.exception.DownstreamServiceException;
import com.shopMeta.cart_service.exception.InsufficientStockException;
import com.shopMeta.cart_service.exception.ResourceNotFoundException;
import com.shopMeta.cart_service.security.AuthenticatedUser;
import com.shopMeta.cart_service.service.CartService;

/**
 * Web-layer tests: routing, validation errors and the mapping of service exceptions to HTTP
 * statuses. The filter chain is off (D-038); the principal is placed in the security context
 * directly so {@code @AuthenticationPrincipal} resolves without the JWT filter.
 */
@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

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

    @Test
    void getCart_returnsTheCartDirectly() throws Exception {

        given(cartService.getCart(1L)).willReturn(CartResponse.empty(1L, null));

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void addItem_returnsTheUpdatedCart() throws Exception {

        given(cartService.addItem(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any())).willReturn(CartResponse.empty(1L, 5L));

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 50, \"quantity\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(5));
    }

    @Test
    void addItem_withMissingFields_returnsValidationMap() throws Exception {

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.productId").exists())
                .andExpect(jsonPath("$.data.quantity").exists());
    }

    @Test
    void addItem_withZeroQuantity_returnsValidationMap() throws Exception {

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 50, \"quantity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.quantity").exists());
    }

    @Test
    void updateItem_setsQuantity() throws Exception {

        given(cartService.updateItem(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
                .willReturn(CartResponse.empty(1L, 3L));

        mockMvc.perform(put("/api/v1/cart/items/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 3}"))
                .andExpect(status().isOk());
    }

    @Test
    void removeItem_returnsTheUpdatedCart() throws Exception {

        given(cartService.removeItem(1L, 7L)).willReturn(CartResponse.empty(1L, 3L));

        mockMvc.perform(delete("/api/v1/cart/items/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(3));
    }

    @Test
    void clearCart_emptiesTheCart() throws Exception {

        given(cartService.clearCart(1L)).willReturn(CartResponse.empty(1L, 3L));

        mockMvc.perform(delete("/api/v1/cart"))
                .andExpect(status().isOk());
    }

    @Test
    void validate_returnsTheValidationAnswer() throws Exception {

        given(cartService.validate(1L)).willReturn(CartValidationResponse.builder()
                .valid(true).itemCount(2).issues(List.of())
                .checkedAt(java.time.LocalDateTime.now()).build());

        mockMvc.perform(post("/api/v1/cart/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.itemCount").value(2));
    }

    @Test
    void unknownItem_isMappedToNotFoundEnvelope() throws Exception {

        willThrow(new ResourceNotFoundException("Cart item 99 was not found in your cart"))
                .given(cartService).removeItem(1L, 99L);

        mockMvc.perform(delete("/api/v1/cart/items/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cart item 99 was not found in your cart"));
    }

    @Test
    void deadDownstreamService_isMappedTo503() throws Exception {

        willThrow(new DownstreamServiceException(
                "Could not reach product-service. Please try again shortly.",
                new RuntimeException("boom")))
                .given(cartService).getCart(1L);

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void insufficientStock_isMappedTo409WithTheProduct() throws Exception {

        willThrow(new InsufficientStockException(
                "Only 2 unit(s) of product 50 are available", 50L, 2))
                .given(cartService).addItem(org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 50, \"quantity\": 5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.productId").value(50))
                .andExpect(jsonPath("$.data.available").value(2));
    }

    @Test
    void totalsAndMoney_areRenderedFromTheServiceAnswer() throws Exception {

        given(cartService.getCart(1L)).willReturn(CartResponse.builder()
                .cartId(3L).userId(1L)
                .items(List.of())
                .totalItems(4)
                .distinctItems(2)
                .subtotal(new BigDecimal("159998.00"))
                .currency("INR")
                .hasWarnings(false)
                .warnings(List.of())
                .updatedAt(java.time.LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.distinctItems").value(2))
                .andExpect(jsonPath("$.subtotal").value(159998.00))
                .andExpect(jsonPath("$.currency").value("INR"));
    }
}
