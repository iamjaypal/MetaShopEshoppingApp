package com.shopMeta.inventory_service.exception;

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

import com.shopMeta.inventory_service.dto.ApiResponse;

import jakarta.validation.ConstraintViolationException;

/**
 * Global exception handling (spec 5.3). Errors use the shared {@code ApiResponse} envelope
 * ({@code success=false, message, data}) with the appropriate HTTP status.
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
     * All-or-nothing reservation failed: 409 with the offending lines in {@code data} (spec 19).
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Object>> handleInsufficientStock(
            InsufficientStockException exception) {

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message(exception.getMessage())
                .data(exception.getUnavailableItems())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Two writers raced on the same stock row and the loser's {@code @Version} was stale (spec 9.4).
     * The caller may retry.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException exception) {

        log.warn("Optimistic lock conflict: {}", exception.getMessage());

        return error(HttpStatus.CONFLICT,
                "The stock for this product was updated concurrently. Please retry.");
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
