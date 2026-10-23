package com.shopMeta.auth_service.exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.shopMeta.auth_service.dto.ApiResponse;

import jakarta.validation.ConstraintViolationException;

/**
 * Global exception handling (spec 5.3). Errors use the shared {@code ApiResponse} envelope
 * ({@code success=false, message, data}) with the appropriate HTTP status.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception) {

        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidCredentials(
            InvalidCredentialsException exception) {

        return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidToken(InvalidTokenException exception) {

        return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccountLocked(AccountLockedException exception) {

        return error(HttpStatus.LOCKED, exception.getMessage());
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccountDisabled(
            AccountDisabledException exception) {

        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException exception) {

        return error(HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Object>> handleLocked(LockedException exception) {

        return error(HttpStatus.LOCKED,
                "Account is temporarily locked due to too many failed login attempts. Try again later.");
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Object>> handleDisabled(DisabledException exception) {

        return error(HttpStatus.FORBIDDEN, "Account is disabled");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentials(BadCredentialsException exception) {

        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password");
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
