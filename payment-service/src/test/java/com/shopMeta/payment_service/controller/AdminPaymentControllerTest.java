package com.shopMeta.payment_service.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shopMeta.payment_service.dto.PaymentPageResponse;
import com.shopMeta.payment_service.dto.PaymentResponse;
import com.shopMeta.payment_service.entity.PaymentStatus;
import com.shopMeta.payment_service.security.AuthenticatedUser;
import com.shopMeta.payment_service.service.PaymentService;

/**
 * Web-layer tests of the admin endpoints: routing and the paging/filter delegation. The ADMIN
 * role rule itself is enforced by the security chain (off here, D-038) and covered by the
 * gateway's route tests.
 */
@WebMvcTest(com.shopMeta.payment_service.controller.AdminPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

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
    void listPayments_withAStatusFilter_passesItThrough() throws Exception {

        given(paymentService.listAll(eq(PaymentStatus.SUCCEEDED), eq(null), eq(0), eq(20)))
                .willReturn(PaymentPageResponse.builder()
                        .content(List.of()).page(0).size(20).totalElements(0)
                        .totalPages(0).last(true).build());

        mockMvc.perform(get("/api/v1/admin/payments").param("status", "SUCCEEDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void listPayments_withAnOrderFilter_passesItThrough() throws Exception {

        given(paymentService.listAll(eq(null), eq(100L), eq(0), eq(20)))
                .willReturn(PaymentPageResponse.builder()
                        .content(List.of(org.mockito.Mockito.mock(PaymentResponse.class)))
                        .page(0).size(20).totalElements(1).totalPages(1).last(true).build());

        mockMvc.perform(get("/api/v1/admin/payments").param("orderId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
