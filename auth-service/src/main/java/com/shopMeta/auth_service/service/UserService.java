package com.shopMeta.auth_service.service;

import com.shopMeta.auth_service.dto.ChangePasswordRequest;
import com.shopMeta.auth_service.dto.UpdateProfileRequest;
import com.shopMeta.auth_service.dto.UserSummary;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.exception.BusinessRuleException;
import com.shopMeta.auth_service.exception.ResourceNotFoundException;
import com.shopMeta.auth_service.repository.UserRepository;
import com.shopMeta.auth_service.util.UserMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User profile operations (spec 7.3): read/update own profile and change the password.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public UserSummary getProfile(Long userId) {
        return UserMapper.toSummary(requireUser(userId));
    }

    @Transactional
    public UserSummary updateProfile(Long userId, UpdateProfileRequest request) {

        User user = requireUser(userId);

        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPhone(request.getPhone());

        return UserMapper.toSummary(userRepository.save(user));
    }

    /**
     * Changes the password after verifying the current one. All refresh tokens are revoked so
     * every other session has to re-authenticate.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {

        User user = requireUser(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(userId);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
