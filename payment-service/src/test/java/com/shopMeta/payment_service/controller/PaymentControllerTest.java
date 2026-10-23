package com.shopMeta.payment_service.controller;

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

import com.shopMeta.payment_service.dto.PaymentPageResponse;
import com.shopMeta.payment_service.dto.PaymentResponse;
import com.shopMeta.payment_service.entity.PaymentMethod;
import com.shopMeta.payment_service.entity.PaymentStatus;
import com.shopMeta.payment_service.exception.BusinessRuleException;
import com.shopMeta.payment_service.exception.DownstreamServiceException;
import com.shopMeta.payment_service.exception.ResourceNotFoundException;
import com.shopMeta.payment_service.security.AuthenticatedUser;
import com.shopMeta.payment_service.service.PaymentService;

/**
 * Web-layer tests: routing, validation errors and the mapping of service exceptions to HTTP
 * statuses. The filter chain is off (D-038); the principal is placed in the security context
 * directly so {@code @AuthenticationPrincipal} resolves without the JWT filter.
 */
@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

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

    static PaymentResponse captured() {

        return PaymentResponse.builder()
                .id(500L)
                .orderId(100L)
                .userId(1L)
                .amount(new BigDecimal("449.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCEEDED)
                .gatewayReference("MOCK-100-abcd1234")
                .build();
    }

    static PaymentResponse declined() {

        return PaymentResponse.builder()
                .id(501L)
                .orderId(100L)
                .userId(1L)
                .amount(new BigDecimal("449.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.FAILED)
                .failureReason("Declined by the mock gateway")
                .build();
    }

    // ------------------------------------------------------------------ pay

    @Test
    void pay_captured_returns201WithTheLedgerEntry() throws Exception {

        given(paymentService.pay(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willReturn(captured());

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": 100, \"method\": \"CARD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.gatewayReference").value("MOCK-100-abcd1234"));
    }

    @Test
    void pay_declined_returns200WithTheRecordedAttempt() throws Exception {

        given(paymentService.pay(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willReturn(declined());

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": 100, \"method\": \"CARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason").value("Declined by the mock gateway"));
    }

    @Test
    void pay_withMissingFields_returnsValidationMap() throws Exception {

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.orderId").exists())
                .andExpect(jsonPath("$.data.method").exists());
    }

    @Test
    void pay_forASettledOrder_returns422() throws Exception {

        given(paymentService.pay(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willThrow(new BusinessRuleException(
                        "Order 100 is CONFIRMED — only orders waiting for payment can be paid"));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": 100, \"method\": \"CARD\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void pay_forSomeoneElsesOrder_returns404() throws Exception {

        given(paymentService.pay(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willThrow(new ResourceNotFoundException("Order 100 was not found"));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": 100, \"method\": \"CARD\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void pay_withOrderServiceDown_returns503() throws Exception {

        given(paymentService.pay(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willThrow(new DownstreamServiceException(
                        "Could not reach order-service. Please try again shortly.", null));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": 100, \"method\": \"CARD\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Could not reach order-service. Please try again shortly."));
    }

    // ------------------------------------------------------------------ reads

    @Test
    void getPayment_returnsTheAttempt() throws Exception {

        given(paymentService.getPayment(1L, 500L, false)).willReturn(captured());

        mockMvc.perform(get("/api/v1/payments/500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.orderId").value(100));
    }

    @Test
    void getPayment_unknown_returns404Envelope() throws Exception {

        given(paymentService.getPayment(1L, 404L, false))
                .willThrow(new ResourceNotFoundException("Payment 404 was not found"));

        mockMvc.perform(get("/api/v1/payments/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listMyPayments_returnsThePage() throws Exception {

        given(paymentService.listMyPayments(eq(1L), eq(0), eq(20)))
                .willReturn(PaymentPageResponse.builder()
                        .content(List.of(captured()))
                        .page(0).size(20).totalElements(1).totalPages(1).last(true)
                        .build());

        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(500))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }
}
