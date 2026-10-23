package com.shopMeta.payment_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.payment_service.dto.ApiResponse;
import com.shopMeta.payment_service.dto.CreatePaymentRequest;
import com.shopMeta.payment_service.dto.PaymentPageResponse;
import com.shopMeta.payment_service.dto.PaymentResponse;
import com.shopMeta.payment_service.entity.PaymentStatus;
import com.shopMeta.payment_service.exception.UnauthenticatedException;
import com.shopMeta.payment_service.security.AuthenticatedUser;
import com.shopMeta.payment_service.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The customer's payments (spec 12). The caller's identity comes from the token, never from the
 * request — there is no way to pay for someone else's order or to read someone else's payments.
 */
@Tag(name = "Payments", description = "Pay for an order and read the payment history")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** Takes a payment for the order through the mock gateway. */
    @Operation(summary = "Pay for an order",
            description = "Body: orderId + method (CARD / UPI / WALLET). The amount is the "
                    + "order's authoritative total read from order-service. A captured charge "
                    + "confirms the order (stock sold, cart cleared); a declined charge cancels "
                    + "the order and releases the stock. The charge is reported as 201 "
                    + "(captured) or 200 with success=false (declined — the attempt is recorded "
                    + "in the ledger either way).")
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreatePaymentRequest request) {

        PaymentResponse payment = paymentService.pay(requireUserId(user), request);

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<PaymentResponse>builder()
                    .success(true)
                    .message("Payment captured; the order is confirmed")
                    .data(payment)
                    .build());
        }

        // Declined — the attempt is a recorded fact, its outcome is data, not an HTTP error.
        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
                .success(false)
                .message(payment.getFailureReason())
                .data(payment)
                .build());
    }

    /** The caller's payment attempts, newest first. */
    @Operation(summary = "List your payment attempts",
            description = "Paged, newest first. Declined attempts stay in the ledger.")
    @GetMapping
    public ResponseEntity<PaymentPageResponse> listMyPayments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        return ResponseEntity.ok(paymentService.listMyPayments(requireUserId(user), page, size));
    }

    /** One payment attempt — the owner, or an admin. */
    @Operation(summary = "Get one payment attempt",
            description = "Owners see their own attempts, admins any attempt. A customer asking "
                    + "for someone else's payment gets the same 404 as a missing one.")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {

        return ResponseEntity.ok(paymentService.getPayment(
                requireUserId(user), id, user != null && user.isAdmin()));
    }

    /** The payment ledger belongs to the token subject. */
    private static Long requireUserId(AuthenticatedUser user) {

        if (user == null || user.userId() == null) {
            throw new UnauthenticatedException("The access token does not identify a user");
        }
        return user.userId();
    }
}
