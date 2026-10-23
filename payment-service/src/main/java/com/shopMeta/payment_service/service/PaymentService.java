package com.shopMeta.payment_service.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.shopMeta.payment_service.client.dto.OrderView;
import com.shopMeta.payment_service.dto.CreatePaymentRequest;
import com.shopMeta.payment_service.dto.PaymentPageResponse;
import com.shopMeta.payment_service.dto.PaymentResponse;
import com.shopMeta.payment_service.entity.Payment;
import com.shopMeta.payment_service.entity.PaymentStatus;
import com.shopMeta.payment_service.exception.BusinessRuleException;
import com.shopMeta.payment_service.exception.DownstreamServiceException;
import com.shopMeta.payment_service.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The payment flow (spec 12). One attempt:
 *
 * <ol>
 *   <li>read the order from order-service's <strong>authoritative</strong> internal API — the
 *       amount is always the order's frozen total, never anything the client sent. A foreign
 *       order is the same 404 as a missing one; an order that is not {@code CREATED} is a 422.</li>
 *   <li>guard the ledger: no SUCCEEDED payment for the order (already paid), no PENDING attempt
 *       (double click).</li>
 *   <li>record the attempt as {@code PENDING}, then charge through the mock gateway — no Feign
 *       call inside a transaction (D-062/D-068 rule).</li>
 *   <li>a captured charge is reported to order-service's {@code payment-confirmed} callback (which
 *       confirms the stock and empties the cart). A decline is reported to
 *       {@code payment-failed} (which releases the stock and cancels the order).</li>
 * </ol>
 *
 * <p>Failure honesty: a charge that was <em>captured</em> but could not be confirmed (order-service
 * down, or it refused because the reservation expired) is kept {@code SUCCEEDED} in the ledger —
 * the money is real; only the confirmation is pending — and the caller gets the 503/422, never a
 * fake success. A declined charge is reported to order-service best-effort and stays {@code FAILED}
 * in the ledger either way; the customer retries with a new attempt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String ORDER_WAITING_FOR_PAYMENT = "CREATED";

    private final DownstreamClients downstreamClients;
    private final PaymentStore paymentStore;
    private final MockPaymentGateway mockGateway;

    public PaymentResponse pay(Long userId, CreatePaymentRequest request) {

        // 1. The authoritative order — owner, status, frozen total.
        OrderView order = downstreamClients.readOrder(request.getOrderId());

        if (!userId.equals(order.getUserId())) {
            // Same 404 as a missing order — ids cannot be probed (spec 18).
            throw new ResourceNotFoundException(
                    "Order " + request.getOrderId() + " was not found");
        }
        if (!ORDER_WAITING_FOR_PAYMENT.equals(order.getStatus())) {
            throw new BusinessRuleException("Order " + order.getId() + " is " + order.getStatus()
                    + " — only orders waiting for payment can be paid");
        }

        // 2. Ledger guards.
        if (paymentStore.hasSucceededForOrder(order.getId())) {
            throw new BusinessRuleException(
                    "Order " + order.getId() + " is already paid");
        }
        if (paymentStore.hasPendingForOrder(order.getId())) {
            throw new BusinessRuleException("A payment attempt for order " + order.getId()
                    + " is already in progress — wait for it to finish");
        }

        // 3. Record the attempt, then take the charge (no transaction around the gateway).
        Payment payment = paymentStore.create(Payment.builder()
                .orderId(order.getId())
                .userId(userId)
                .amount(order.getTotal())
                .method(request.getMethod())
                .status(PaymentStatus.PENDING)
                .build());

        // 4. Charge.
        MockPaymentGateway.ChargeResult charge = mockGateway.charge(order.getId());

        if (charge.captured()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setGatewayReference(charge.reference());
            payment = paymentStore.save(payment);

            try {
                downstreamClients.reportPaymentConfirmed(order.getId());
                log.info("Payment {} captured for order {} ({} {})", payment.getId(),
                        order.getId(), order.getTotal(), payment.getMethod());
            } catch (BusinessRuleException ex) {
                // The order refused the confirmation — e.g. its stock reservation expired while
                // the payment was in flight, or another attempt won. The ledger must not claim
                // success over a refused charge — and the caller must not mistake this for a
                // plain decline: the money WAS taken, so the refusal surfaces as a 422.
                log.warn("Charge for order {} captured but not applicable: {}", order.getId(),
                        ex.getMessage());
                failed(payment, ex.getMessage());
                throw ex;
            } catch (DownstreamServiceException ex) {
                // The charge is real; only the confirmation is pending. The ledger keeps the
                // truth (SUCCEEDED), the caller gets the 503 — never a fake success (spec 19).
                log.error("Charge for order {} captured but order-service could not be told: {}",
                        order.getId(), ex.getMessage());
                throw ex;
            }

            return toResponse(payment);
        }

        // Declined.
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(charge.declineReason());
        payment = paymentStore.save(payment);
        log.info("Payment {} for order {} declined: {}", payment.getId(), order.getId(),
                charge.declineReason());

        try {
            downstreamClients.reportPaymentFailed(order.getId());
        } catch (DownstreamServiceException ex) {
            // The ledger is truthful (FAILED); the order side reconciles through its own
            // reservation TTL / the customer's cancel. Never fail the decline report itself.
            log.error("Payment {} declined but order-service could not be told: {}",
                    payment.getId(), ex.getMessage());
        } catch (BusinessRuleException ex) {
            // E.g. another decline already cancelled the order — replay, harmless.
            log.warn("Decline of order {} already applied: {}", order.getId(), ex.getMessage());
        }

        return toResponse(payment);
    }

    // ------------------------------------------------------------------ reads

    public PaymentResponse getPayment(Long callerId, Long paymentId, boolean isAdmin) {

        Payment payment = paymentStore.find(paymentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payment " + paymentId + " was not found"));

        if (!isAdmin && !callerId.equals(payment.getUserId())) {
            // Same 404 as a missing payment — ids cannot be probed (spec 18).
            throw new ResourceNotFoundException(
                    "Payment " + paymentId + " was not found");
        }

        return toResponse(payment);
    }

    public PaymentPageResponse listMyPayments(Long userId, Integer page, Integer size) {

        return toPageResponse(paymentStore.pageByUser(userId, pageable(page, size)));
    }

    public PaymentPageResponse listAll(PaymentStatus status, Long orderId,
            Integer page, Integer size) {

        Pageable pageable = pageable(page, size);

        Page<Payment> payments = orderId != null
                ? paymentStore.pageByOrder(orderId, pageable)
                : paymentStore.pageByStatus(status, pageable);

        return toPageResponse(payments);
    }

    // ------------------------------------------------------------------ building

    private PaymentResponse failed(Payment payment, String reason) {

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        return toResponse(paymentStore.save(payment));
    }

    private PaymentResponse toResponse(Payment payment) {

        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .gatewayReference(payment.getGatewayReference())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private PaymentPageResponse toPageResponse(Page<Payment> page) {

        List<PaymentResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PaymentPageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private static Pageable pageable(Integer page, Integer size) {

        int pageNumber = page == null ? 0 : Math.max(0, page);
        int pageSize = size == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        return PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
