package com.shopMeta.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

/**
 * Safe projection of a user (never exposes the password hash).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummary {

    private Long id;

    private String firstName;

    private String lastName;

    private String email;

    private String phone;

    private Boolean enabled;

    private Boolean emailVerified;

    private Set<String> roles;
}
