package com.shopMeta.notification_service.entity;

/**
 * Lifecycle of one notification row (spec 13).
 *
 * <p>Stored as a {@code VARCHAR(20)} with a CHECK constraint rather than a MySQL ENUM, for the same
 * reason as inventory-service (D-049): adding a state later is a migration, not a table rebuild.
 */
public enum NotificationStatus {

    /** Written before the SMTP call; a row stuck here means the process died mid-send. */
    PENDING,

    /** The mail server accepted the message. */
    SENT,

    /** Every delivery attempt failed. Retryable from the admin API. */
    FAILED
}
