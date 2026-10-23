package com.shopMeta.payment_service.entity;

/**
 * How the customer chose to pay. All methods run through the same mock gateway today; the value is
 * recorded so the ledger and the future notification emails can distinguish them.
 */
public enum PaymentMethod {
    CARD,
    UPI,
    WALLET
}
