package com.shopMeta.auth_service.repository;

import com.shopMeta.auth_service.entity.Enums.RoleName;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.exception.InvalidRequestException;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

import org.springframework.data.jpa.domain.Specification;

/**
 * Filters for {@code GET /api/v1/admin/users} (spec 7.3): email (partial), role, enabled.
 */
public final class UserSpecification {

    private UserSpecification() {
    }

    public static Specification<User> filter(String email, String role, Boolean enabled) {

        Specification<User> specification = (root, query, cb) -> cb.conjunction();

        if (email != null && !email.isBlank()) {
            String pattern = "%" + email.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("email")), pattern));
        }

        if (enabled != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("enabled"), enabled));
        }

        if (role != null && !role.isBlank()) {
            RoleName roleName = parseRole(role);
            specification = specification.and((root, query, cb) -> {
                query.distinct(true);
                Join<Object, Object> roles = root.join("roles", JoinType.INNER);
                return cb.equal(roles.get("name"), roleName);
            });
        }

        return specification;
    }

    private static RoleName parseRole(String role) {

        try {
            return RoleName.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                    "Unknown role '" + role + "'. Allowed values: CUSTOMER, ADMIN");
        }
    }
}
