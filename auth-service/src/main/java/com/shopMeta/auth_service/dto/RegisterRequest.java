package com.shopMeta.auth_service.dto;

import com.shopMeta.auth_service.util.PasswordPolicy;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 80, message = "First name cannot exceed 80 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 80, message = "Last name cannot exceed 80 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email;

    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be in international format, e.g. +919876543210")
    private String phone;

    @NotBlank(message = "Password is required")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    private String password;
}
