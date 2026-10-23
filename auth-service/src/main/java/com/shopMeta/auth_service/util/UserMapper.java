package com.shopMeta.auth_service.util;

import com.shopMeta.auth_service.dto.UserSummary;
import com.shopMeta.auth_service.entity.User;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps a {@link User} entity to its safe {@link UserSummary} projection (never the password hash).
 */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserSummary toSummary(User user) {

        if (user == null) {
            return null;
        }

        Set<String> roles = user.getRoles() == null
                ? Set.of()
                : user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return UserSummary.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .enabled(user.getEnabled())
                .emailVerified(user.getEmailVerified())
                .roles(roles)
                .build();
    }
}
