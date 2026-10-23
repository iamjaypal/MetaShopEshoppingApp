package com.shopMeta.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Create/update body for the address book (spec 7.3).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name cannot exceed 120 characters")
    private String fullName;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be in international format, e.g. +919876543210")
    private String phone;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 200, message = "Address line 1 cannot exceed 200 characters")
    private String line1;

    @Size(max = 200, message = "Address line 2 cannot exceed 200 characters")
    private String line2;

    @NotBlank(message = "City is required")
    @Size(max = 80, message = "City cannot exceed 80 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 80, message = "State cannot exceed 80 characters")
    private String state;

    @NotBlank(message = "Postal code is required")
    @Size(max = 12, message = "Postal code cannot exceed 12 characters")
    private String postalCode;

    @Size(min = 2, max = 2, message = "Country must be a 2-letter ISO code")
    private String country;

    /** When true, this address becomes the user's default address. */
    private Boolean isDefault;
}
