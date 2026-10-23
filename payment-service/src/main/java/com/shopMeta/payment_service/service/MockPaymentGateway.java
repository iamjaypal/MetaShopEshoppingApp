package com.shopMeta.payment_service.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shopMeta.payment_service.config.PaymentProperties;

import lombok.RequiredArgsConstructor;

/**
 * The mock gateway (spec 12). There is no real PSP behind this service, so the charge outcome is
 * decided by {@code metashop.payment.gateway-mode}: {@code SUCCESS} captures every attempt,
 * {@code FAILURE} declines every attempt (to demo the release path end-to-end), {@code RANDOM}
 * flips a coin.
 *
 * <p>A real gateway integration replaces this bean — nothing else in the service changes: the
 * ledger, the reporting to order-service and the retries stay exactly as they are.
 */
@Component
@RequiredArgsConstructor
public class MockPaymentGateway {

    /** Outcome of a charge: the reference is what a real gateway would call the txn id. */
    public record ChargeResult(boolean captured, String reference, String declineReason) {

        public static ChargeResult captured(String reference) {
            return new ChargeResult(true, reference, null);
        }

        public static ChargeResult declined(String reason) {
            return new ChargeResult(false, null, reason);
        }
    }

    private final PaymentProperties properties;

    public ChargeResult charge(Long orderId) {

        return switch (properties.getGatewayMode()) {
            case SUCCESS -> ChargeResult.captured(reference(orderId));
            case FAILURE -> ChargeResult.declined("Declined by the mock gateway");
            case RANDOM -> Math.random() < 0.5
                    ? ChargeResult.captured(reference(orderId))
                    : ChargeResult.declined("Declined by the mock gateway");
        };
    }

    private static String reference(Long orderId) {
        return "MOCK-" + orderId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
