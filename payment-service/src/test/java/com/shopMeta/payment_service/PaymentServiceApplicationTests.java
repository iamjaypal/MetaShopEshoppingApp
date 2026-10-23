package com.shopMeta.payment_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
