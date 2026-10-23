package com.shopMeta.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Address projection (spec 7.3).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {

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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
