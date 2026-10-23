package com.shopMeta.cart_service.controller;

import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shopMeta.cart_service.service.CartService;

/**
 * The internal endpoint order-service calls after an order is confirmed. Plain JSON, plain 204.
 * The {@code X-Internal-Token} check itself is covered by the filter — here the route exists and
 * delegates.
 */
@WebMvcTest(InternalCartController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalCartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Test
    void clearCart_returns204() throws Exception {

        mockMvc.perform(delete("/api/v1/internal/cart/42"))
                .andExpect(status().isNoContent());

        then(cartService).should().clearCartForUser(42L);
    }
}
