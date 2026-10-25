package com.shopMeta.notification_service.exception;

/**
 * An event arrived whose payload could not be mapped to the expected shape.
 *
 * <p>This is a permanent failure, so it is thrown rather than swallowed: Spring Kafka's error handler
 * retries it a few times and then publishes it to {@code <topic>.DLT}, where an operator can look at
 * it. The alternative — logging and continuing — would lose the event silently.
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
