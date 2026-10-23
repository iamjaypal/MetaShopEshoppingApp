package com.shopMeta.order_service.client.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirror of cart-service's {@code CartValidationResponse} (spec 10.3): "would this cart check out
 * right now?". Checkout refuses to reserve stock while any issue stands.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartValidationView {

    private boolean valid;

    private int itemCount;

    private List<CartIssueView> issues;

    private LocalDateTime checkedAt;
}
