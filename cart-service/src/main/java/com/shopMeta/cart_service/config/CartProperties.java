package com.shopMeta.cart_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cart rules (spec 10.1).
 */
@Component
@ConfigurationProperties(prefix = "metashop.cart")
@Getter
@Setter
public class CartProperties {

    /**
     * Maximum quantity of one product in a cart. Mirrors the {@code CHECK (quantity BETWEEN 1 AND
     * 20)} in the migration; exceeding it is a 422 rather than a database error.
     */
    private int maxQuantityPerItem = 20;

    /**
     * Maximum number of different products in one cart. Not in the spec — it stops a single cart
     * from growing without bound and keeps the downstream batch calls small.
     */
    private int maxDistinctItems = 50;
}
