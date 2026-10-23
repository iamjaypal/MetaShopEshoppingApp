package com.shopMeta.order_service.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.shopMeta.order_service.dto.OrderPageResponse;
import com.shopMeta.order_service.dto.OrderResponse;
import com.shopMeta.order_service.entity.OrderStatus;
import com.shopMeta.order_service.exception.BusinessRuleException;
import com.shopMeta.order_service.security.AuthenticatedUser;
import com.shopMeta.order_service.service.OrderService;

/**
 * Web-layer tests of the admin endpoints: routing and the mapping of service exceptions. The
 * ADMIN role rule itself is enforced by the security chain (off here, D-038) and covered by the
 * gateway's route tests.
 */
@WebMvcTest(com.shopMeta.order_service.controller.AdminOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(2L, "admin@metashop.com", List.of("ADMIN")),
                null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listOrders_withAStatusFilter_passesItThrough() throws Exception {

        given(orderService.listAllOrders(eq(OrderStatus.CREATED), eq(0), eq(20)))
                .willReturn(OrderPageResponse.builder()
                        .content(List.of()).page(0).size(20).totalElements(0)
                        .totalPages(0).last(true).build());

        mockMvc.perform(get("/api/v1/admin/orders").param("status", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void updateStatus_shipsAConfirmedOrder() throws Exception {

        OrderResponse shipped = OrderControllerTest.createdOrder();
        shipped.setStatus(OrderStatus.SHIPPED);
        given(orderService.updateStatus(eq(100L), eq(OrderStatus.SHIPPED))).willReturn(shipped);

        mockMvc.perform(post("/api/v1/admin/orders/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void updateStatus_withAnIllegalTransition_returns422() throws Exception {

        given(orderService.updateStatus(eq(100L), eq(OrderStatus.DELIVERED)))
                .willThrow(new BusinessRuleException("Only shipped orders can be delivered"));

        mockMvc.perform(post("/api/v1/admin/orders/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"DELIVERED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Only shipped orders can be delivered"));
    }
}
