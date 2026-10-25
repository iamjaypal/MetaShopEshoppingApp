package com.shopMeta.notification_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the context starts with the schema Flyway just migrated.
 *
 * <p>Kafka listeners are switched off so the test does not need a broker — the consumer path is
 * covered against a real embedded broker by
 * {@link com.shopMeta.notification_service.kafka.NotificationEventConsumerTest}.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
