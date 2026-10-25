package com.shopMeta.notification_service.event;

/**
 * The topics this service consumes (spec 14.1). Notification appears as a consumer on four of the
 * five domain topics; only {@code product.events} is of no interest to it.
 */
public final class KafkaTopics {

    public static final String USER_EVENTS = "user.events";

    public static final String ORDER_EVENTS = "order.events";

    public static final String PAYMENT_EVENTS = "payment.events";

    public static final String INVENTORY_EVENTS = "inventory.events";

    private KafkaTopics() {
    }
}
