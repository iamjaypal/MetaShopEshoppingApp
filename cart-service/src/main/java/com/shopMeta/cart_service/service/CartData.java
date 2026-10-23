package com.shopMeta.cart_service.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A cart exactly as it is stored, before any catalogue data is fetched.
 *
 * <p>Kept separate from the response DTO so the database read can complete and its transaction close
 * <em>before</em> any network call starts — a downstream call must never run inside a transaction
 * (spec 5.10).
 */
public record CartData(Long cartId, Long userId, LocalDateTime updatedAt, List<Line> lines) {

    /** One stored line: what the cart owns, and nothing else. */
    public record Line(
            Long itemId,
            Long productId,
            int quantity,
            BigDecimal addedPrice,
            LocalDateTime addedAt) {
    }
}
