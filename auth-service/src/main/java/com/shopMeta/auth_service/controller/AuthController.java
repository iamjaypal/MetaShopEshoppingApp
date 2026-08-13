package com.shopMeta.auth_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.auth_service.dto.ApiResponse;
import com.shopMeta.auth_service.dto.LoginRequest;
import com.shopMeta.auth_service.dto.LoginResponse;
import com.shopMeta.auth_service.dto.RegisterRequest;
import com.shopMeta.auth_service.dto.RegisterResponse;
import com.shopMeta.auth_service.service.AuthService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@AllArgsConstructor
public class AuthController {

    private AuthService authservice;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> createUser(@Valid @RequestBody RegisterRequest request) {

        RegisterResponse registerResponse = authservice.register(request);

        ApiResponse<RegisterResponse> response = ApiResponse.<RegisterResponse>builder()
                .success(true)
                .message("User Registered Successfully")
                .data(registerResponse).build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse loginResponse = authservice.login(request);

        ApiResponse<LoginResponse> response = ApiResponse.<LoginResponse>builder()
                .success(true)
                .message("Login Successful")
                .data(loginResponse)
                .build();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

}
