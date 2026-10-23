package com.shopMeta.auth_service.service;

import com.shopMeta.auth_service.dto.AdminUserResponse;
import com.shopMeta.auth_service.dto.PageResponse;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.exception.BusinessRuleException;
import com.shopMeta.auth_service.exception.ResourceNotFoundException;
import com.shopMeta.auth_service.repository.UserRepository;
import com.shopMeta.auth_service.repository.UserSpecification;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin user management (spec 7.3): paginated listing with filters and enable/disable.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(
            String email, String role, Boolean enabled, int page, int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));

        Page<User> users = userRepository.findAll(
                UserSpecification.filter(email, role, enabled), pageable);

        return PageResponse.from(users, this::toResponse);
    }

    @Transactional
    public AdminUserResponse updateStatus(Long callerId, Long userId, Boolean enabled) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (Boolean.FALSE.equals(enabled) && user.getId().equals(callerId)) {
            throw new BusinessRuleException("You cannot disable your own account");
        }

        user.setEnabled(enabled);

        if (Boolean.FALSE.equals(enabled)) {
            // Revoke the user's sessions immediately.
            refreshTokenService.revokeAllForUser(userId);
        }

        return toResponse(userRepository.save(user));
    }

    private AdminUserResponse toResponse(User user) {

        Set<String> roles = user.getRoles() == null
                ? Set.of()
                : user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return AdminUserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .enabled(user.getEnabled())
                .emailVerified(user.getEmailVerified())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
