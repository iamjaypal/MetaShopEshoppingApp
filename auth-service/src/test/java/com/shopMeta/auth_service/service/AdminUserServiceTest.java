package com.shopMeta.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.shopMeta.auth_service.dto.AdminUserResponse;
import com.shopMeta.auth_service.dto.PageResponse;
import com.shopMeta.auth_service.entity.Role;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.entity.Enums.RoleName;
import com.shopMeta.auth_service.exception.BusinessRuleException;
import com.shopMeta.auth_service.exception.ResourceNotFoundException;
import com.shopMeta.auth_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AdminUserService adminUserService;

    private User user(Long id, boolean enabled) {
        return User.builder()
                .id(id)
                .firstName("Test")
                .lastName("User")
                .email("user" + id + "@example.com")
                .enabled(enabled)
                .emailVerified(true)
                .roles(new HashSet<>(Set.of(
                        Role.builder().id(1L).name(RoleName.CUSTOMER).build())))
                .build();
    }

    @Test
    void listUsers_returnsPaginatedResult() {

        Page<User> page = new PageImpl<>(List.of(user(2L, true), user(1L, true)),
                PageRequest.of(0, 20), 2);

        given(userRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(page);

        PageResponse<AdminUserResponse> response =
                adminUserService.listUsers(null, null, null, 0, 20);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).getRoles()).containsExactly("CUSTOMER");
        assertThat(response.last()).isTrue();
    }

    @Test
    void listUsers_clampsPageSize() {

        Page<User> page = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);

        given(userRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(page);

        PageResponse<AdminUserResponse> response =
                adminUserService.listUsers(null, null, null, -1, 5000);

        assertThat(response.size()).isEqualTo(100);
        assertThat(response.page()).isZero();
    }

    @Test
    void updateStatus_disablingRevokesSessions() {

        User user = user(2L, true);
        given(userRepository.findById(2L)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        AdminUserResponse response = adminUserService.updateStatus(1L, 2L, false);

        assertThat(response.getEnabled()).isFalse();
        verify(refreshTokenService).revokeAllForUser(2L);
    }

    @Test
    void updateStatus_enablingDoesNotRevokeSessions() {

        User user = user(2L, false);
        given(userRepository.findById(2L)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        AdminUserResponse response = adminUserService.updateStatus(1L, 2L, true);

        assertThat(response.getEnabled()).isTrue();
        verify(refreshTokenService, org.mockito.Mockito.never()).revokeAllForUser(any());
    }

    @Test
    void updateStatus_disablingSelf_isRejected() {

        User admin = user(1L, true);
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminUserService.updateStatus(1L, 1L, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot disable your own account");

        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateStatus_missingUser_throwsNotFound() {

        given(userRepository.findById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.updateStatus(1L, 42L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
