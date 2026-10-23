package com.shopMeta.order_service.exception;

import java.util.List;

import com.shopMeta.order_service.client.dto.UnavailableItemView;

import lombok.Getter;

/**
 * inventory-service refused the all-or-nothing reservation (spec 9.3 / 19). The whole checkout is
 * refused with 409 and {@code data} lists the offending lines with what is really available, so
 * the storefront can show "only 2 left" next to the right product. The order row created moments
 * earlier is compensated to CANCELLED by the service before this is thrown.
 */
@Getter
public class InsufficientStockException extends RuntimeException {

    private final transient List<UnavailableItemView> unavailableItems;

    public InsufficientStockException(String message, List<UnavailableItemView> unavailableItems) {
        super(message);
        this.unavailableItems = List.copyOf(unavailableItems);
    }
}
