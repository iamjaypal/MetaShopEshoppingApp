package com.shopMeta.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.shopMeta.auth_service.dto.ChangePasswordRequest;
import com.shopMeta.auth_service.dto.UpdateProfileRequest;
import com.shopMeta.auth_service.dto.UserSummary;
import com.shopMeta.auth_service.entity.Role;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.entity.Enums.RoleName;
import com.shopMeta.auth_service.exception.BusinessRuleException;
import com.shopMeta.auth_service.exception.ResourceNotFoundException;
import com.shopMeta.auth_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserService userService;

    private User user() {
        return User.builder()
                .id(1L)
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .phone("+919000000001")
                .passwordHash("old-hash")
                .enabled(true)
                .emailVerified(false)
                .roles(new HashSet<>(Set.of(
                        Role.builder().id(1L).name(RoleName.CUSTOMER).build())))
                .build();
    }

    @Test
    void getProfile_returnsSummary() {

        given(userRepository.findById(1L)).willReturn(Optional.of(user()));

        UserSummary profile = userService.getProfile(1L);

        assertThat(profile.getId()).isEqualTo(1L);
        assertThat(profile.getEmail()).isEqualTo("test@example.com");
        assertThat(profile.getRoles()).containsExactly("CUSTOMER");
    }

    @Test
    void getProfile_missingUser_throwsNotFound() {

        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_updatesNameAndPhone() {

        User user = user();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName("  Updated  ");
        request.setLastName("Name");
        request.setPhone("+919000000002");

        UserSummary summary = userService.updateProfile(1L, request);

        assertThat(summary.getFirstName()).isEqualTo("Updated");
        assertThat(summary.getLastName()).isEqualTo("Name");
        assertThat(summary.getPhone()).isEqualTo("+919000000002");
    }

    @Test
    void changePassword_wrongCurrentPassword_isRejected() {

        User user = user();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "old-hash")).willReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong");
        request.setNewPassword("N3w@Passw0rd");

        assertThatThrownBy(() -> userService.changePassword(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void changePassword_success_revokesSessions() {

        User user = user();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Secret@123", "old-hash")).willReturn(true);
        given(passwordEncoder.encode("N3w@Passw0rd")).willReturn("new-hash");
        given(userRepository.save(user)).willReturn(user);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("Secret@123");
        request.setNewPassword("N3w@Passw0rd");

        userService.changePassword(1L, request);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenService).revokeAllForUser(1L);
    }
}
