package com.shopMeta.order_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirror of inventory-service's {@code UnavailableItem}: one line that could not be reserved,
 * reported in the 409 body so the customer sees exactly what is missing (spec 19).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnavailableItemView {

    private Long productId;

    private int requested;

    private int available;
}
