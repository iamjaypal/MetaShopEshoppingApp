package com.shopMeta.order_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Order money rules (spec 11). A flat shipping fee is added to the subtotal and waived once the
 * subtotal reaches the free-shipping threshold.
 */
@Component
@ConfigurationProperties(prefix = "metashop.order")
@Getter
@Setter
public class OrderProperties {

    /** Flat shipping fee in the order currency. */
    private BigDecimal shippingFee = new BigDecimal("49");

    /** Orders whose subtotal reaches this amount ship free. */
    private BigDecimal freeShippingThreshold = new BigDecimal("499");
}
