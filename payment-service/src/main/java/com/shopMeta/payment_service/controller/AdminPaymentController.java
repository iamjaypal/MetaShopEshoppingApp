package com.shopMeta.payment_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.payment_service.dto.PaymentPageResponse;
import com.shopMeta.payment_service.entity.PaymentStatus;
import com.shopMeta.payment_service.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Admin view over the payment ledger (spec 12). The ADMIN role is enforced by the security rule
 * on {@code /api/v1/admin/**}. Filter by status, by order, or neither; both filters at once
 * resolves to the order's ledger.
 */
@Tag(name = "Payments (admin)", description = "The whole payment ledger")
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "List every user's payment attempts",
            description = "Paged, newest first. Filter by ?status= or ?orderId= (orderId wins "
                    + "when both are given).")
    @GetMapping
    public ResponseEntity<PaymentPageResponse> listPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        return ResponseEntity.ok(
                paymentService.listAll(status, orderId, page, size));
    }
}
