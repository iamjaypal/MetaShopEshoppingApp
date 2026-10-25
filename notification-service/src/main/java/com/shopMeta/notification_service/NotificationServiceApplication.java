package com.shopMeta.notification_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * MetaShop notification service (spec 13).
 *
 * <p>A pure event consumer plus an email sender. It owns no domain data of its own: everything it
 * sends is derived from events published by the other services on Kafka, and the only HTTP surface
 * is an admin view of the notification log.
 *
 * <p>{@link EnableKafka} is declared explicitly even though Boot would wire the listener
 * infrastructure anyway, because the listeners are the point of this service rather than a detail.
 */
@SpringBootApplication
@EnableKafka
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

}
