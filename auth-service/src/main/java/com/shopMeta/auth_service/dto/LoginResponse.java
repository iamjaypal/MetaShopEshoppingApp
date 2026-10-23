package com.shopMeta.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Login/refresh response (spec 7.3):
 * {@code {accessToken, refreshToken, tokenType, expiresIn, user}}.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    /** Access-token lifetime in seconds. */
    private Long expiresIn;

    private UserSummary user;

}
