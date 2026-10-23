package com.shopMeta.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The shipping-address snapshot copied onto an order at checkout (spec 10.2). This is a frozen
 * copy — later edits to the user's address book never move an order that already exists.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAddressResponse {

    private String fullName;

    private String phone;

    private String line1;

    private String line2;

    private String city;

    private String state;

    private String postalCode;

    private String country;
}
