package com.shopMeta.order_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirror of auth-service's {@code AddressResponse} (spec 7.3), fetched through
 * {@code GET /api/v1/internal/users/{userId}/addresses/{addressId}} so checkout can copy the
 * shipping address onto the order.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddressSnapshot {

    private Long id;

    private String fullName;

    private String phone;

    private String line1;

    private String line2;

    private String city;

    private String state;

    private String postalCode;

    private String country;

    private Boolean isDefault;
}
