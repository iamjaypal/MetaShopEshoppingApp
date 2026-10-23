package com.shopMeta.auth_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body for {@code PATCH /api/v1/admin/users/{id}/status} (spec 7.3).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserStatusRequest {

    @NotNull(message = "enabled is required")
    private Boolean enabled;
}
