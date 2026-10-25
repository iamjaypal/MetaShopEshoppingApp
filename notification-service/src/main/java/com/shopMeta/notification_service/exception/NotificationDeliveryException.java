package com.shopMeta.notification_service.exception;

/**
 * The mail server refused the message. Returns 503.
 *
 * <p>Thrown out of the consumer on purpose: Spring Kafka's error handler retries the record three
 * times with exponential backoff and then publishes it to {@code <topic>.DLT} (spec 13 / 14.4). The
 * notification row is already marked FAILED before this is thrown, so the admin retry endpoint can
 * pick it up afterwards.
 */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationDeliveryException(String message) {
        super(message);
    }
}
