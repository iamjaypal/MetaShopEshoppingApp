package com.shopMeta.notification_service.entity;

/**
 * The kinds of email this service can send (spec 13). Deliberately not the same vocabulary as the
 * Kafka event names: several events may map to one notification, and one event may later fan out to
 * more than one.
 */
public enum NotificationType {

    WELCOME,
    PASSWORD_RESET,

    ORDER_CONFIRMED,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    ORDER_CANCELLED,

    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED,

    /** Operational alert, sent to the admin address rather than to a customer. */
    LOW_STOCK
}
