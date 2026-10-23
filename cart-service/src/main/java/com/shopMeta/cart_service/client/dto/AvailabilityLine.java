package com.shopMeta.cart_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of a bulk availability request. {@code quantity} is what the cart would need, so the
 * answer says whether that many can actually be bought.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityLine {

    private Long productId;

    private Integer quantity;
}
