package com.shopMeta.auth_service.service;

import com.shopMeta.auth_service.dto.LoginRequest;
import com.shopMeta.auth_service.dto.LoginResponse;
import com.shopMeta.auth_service.dto.RegisterRequest;
import com.shopMeta.auth_service.dto.RegisterResponse;
import com.shopMeta.auth_service.entity.Role;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.entity.Enums.RoleName;
import com.shopMeta.auth_service.exception.EmailAlreadyExistsException;
import com.shopMeta.auth_service.repository.RoleRepository;
import com.shopMeta.auth_service.repository.UserRepository;
import com.shopMeta.auth_service.security.JwtService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public RegisterResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        Role customerRole = roleRepository
                .findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new RuntimeException("Role CUSTOMER not found"));

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .enabled(true)
                .roles(Set.of(customerRole))
                .build();

        User savedUser = userRepository.save(user);

        return RegisterResponse.builder()
                .id(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .phoneNumber(savedUser.getPhoneNumber())
                .build();
    }

    public LoginResponse login(LoginRequest request) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()));

        String token = jwtService.generateToken(
                (UserDetails) authentication.getPrincipal());

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .build();
    }
}
