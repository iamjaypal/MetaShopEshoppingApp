package com.shopMeta.inventory_service.exception;

import com.shopMeta.inventory_service.dto.UnavailableItem;

import java.util.List;

/**
 * The all-or-nothing reservation failed because at least one line cannot be satisfied (spec 19).
 * Returns 409 with the offending lines so the caller can report them.
 */
public class InsufficientStockException extends RuntimeException {

    private final transient List<UnavailableItem> unavailableItems;

    public InsufficientStockException(String message, List<UnavailableItem> unavailableItems) {
        super(message);
        this.unavailableItems = List.copyOf(unavailableItems);
    }

    public List<UnavailableItem> getUnavailableItems() {
        return unavailableItems;
    }
}
