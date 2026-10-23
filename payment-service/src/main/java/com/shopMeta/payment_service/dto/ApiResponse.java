package com.shopMeta.payment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The shared response envelope (spec 5.3). Success bodies put the payload in {@code data}; error
 * bodies carry {@code success=false} with a message and (where useful) problem details.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

    private boolean success;

    private String message;

    private T data;
}
