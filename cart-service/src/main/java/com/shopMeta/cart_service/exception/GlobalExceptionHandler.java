package com.shopMeta.cart_service.exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.shopMeta.cart_service.dto.ApiResponse;

import jakarta.validation.ConstraintViolationException;

/**
 * Global exception handling (spec 5.3). Errors use the shared {@code ApiResponse} envelope
 * ({@code success=false, message, data}) with the appropriate HTTP status.
 *
 * <p>Downstream failures never leak as 500s: {@code CatalogClient} already funnels every
 * unreachable/timeout/open-breaker outcome into {@link DownstreamServiceException}, which becomes a
 * 503 — the same status the gateway reports for a dead service.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(ResourceNotFoundException exception) {

        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessRule(BusinessRuleException exception) {

        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidRequest(InvalidRequestException exception) {

        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * Stock check failed: 409 with the product and the real number in {@code data}, so the
     * storefront can show "only 2 left" next to the right line (spec 19).
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Object>> handleInsufficientStock(
            InsufficientStockException exception) {

        Map<String, Object> data = Map.of(
                "productId", exception.getProductId(),
                "available", exception.getAvailableQuantity());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message(exception.getMessage())
                .data(data)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * product-service or inventory-service is unreachable, timed out, or its breaker is open after
     * the retry policy. The cart is honest about it instead of rendering stale prices (spec 10.2).
     */
    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<ApiResponse<Object>> handleDownstream(DownstreamServiceException exception) {

        log.warn("Downstream call failed: {}", exception.getMessage());

        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    /**
     * A valid token that does not carry a numeric user subject cannot own a cart (spec 10.3).
     */
    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthenticated(
            UnauthenticatedException exception) {

        return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    /**
     * Two writers raced on the same cart row and the loser's {@code @Version} was stale. The caller
     * may retry.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException exception) {

        log.warn("Optimistic lock conflict: {}", exception.getMessage());

        return error(HttpStatus.CONFLICT,
                "The cart was updated concurrently. Please retry.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException exception) {

        return error(HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException exception) {

        return error(HttpStatus.BAD_REQUEST, "Malformed or missing request body");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException exception) {

        return error(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(
            ConstraintViolationException exception) {

        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
                .success(false)
                .message("Validation Failed")
                .data(errors)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception exception) {

        log.error("Unhandled exception", exception);

        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ApiResponse<Object>> error(HttpStatus status, String message) {

        ApiResponse<Object> response = ApiResponse.<Object>builder()
                .success(false)
                .message(message)
                .data(null)
                .build();

        return ResponseEntity.status(status).body(response);
    }
}
