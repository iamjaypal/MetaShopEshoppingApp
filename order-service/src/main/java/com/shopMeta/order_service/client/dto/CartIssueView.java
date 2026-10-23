package com.shopMeta.order_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirror of cart-service's {@code CartIssue}: one reason a cart line cannot be checked out.
 * {@code type} stays a String so this service does not need to track cart-service's enum.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartIssueView {

    private Long itemId;

    private Long productId;

    private String type;

    private String message;
}
