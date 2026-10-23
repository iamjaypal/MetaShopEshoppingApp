package com.shopMeta.order_service.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shopMeta.order_service.entity.OrderStatus;
import com.shopMeta.order_service.entity.PaymentStatus;
import com.shopMeta.order_service.exception.ResourceNotFoundException;
import com.shopMeta.order_service.service.OrderService;

/**
 * Web-layer tests of the internal payment-result endpoints (payment-service → order-service).
 * The {@code X-Internal-Token} check lives in the security filter (off here, D-038) and is
 * exercised live in the end-to-end run; these tests cover the routing and the 404 mapping.
 */
@WebMvcTest(InternalOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void paymentConfirmed_returnsTheConfirmedOrder() throws Exception {

        given(orderService.paymentConfirmed(100L)).willReturn(confirmed());

        mockMvc.perform(post("/api/v1/internal/orders/100/payment-confirmed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void paymentFailed_returnsTheCancelledOrder() throws Exception {

        given(orderService.paymentFailed(100L)).willReturn(cancelled());

        mockMvc.perform(post("/api/v1/internal/orders/100/payment-failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"));
    }

    private static com.shopMeta.order_service.dto.OrderResponse confirmed() {

        com.shopMeta.order_service.dto.OrderResponse response = OrderControllerTest.createdOrder();
        response.setStatus(OrderStatus.CONFIRMED);
        response.setPaymentStatus(PaymentStatus.PAID);
        return response;
    }

    private static com.shopMeta.order_service.dto.OrderResponse cancelled() {

        com.shopMeta.order_service.dto.OrderResponse response = OrderControllerTest.createdOrder();
        response.setStatus(OrderStatus.CANCELLED);
        response.setPaymentStatus(PaymentStatus.FAILED);
        return response;
    }

    @Test
    void paymentConfirmed_forAnUnknownOrder_returns404() throws Exception {

        given(orderService.paymentConfirmed(404L))
                .willThrow(new ResourceNotFoundException("Order 404 was not found"));

        mockMvc.perform(post("/api/v1/internal/orders/404/payment-confirmed"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
