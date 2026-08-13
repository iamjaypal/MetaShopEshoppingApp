package com.shopMeta.auth_service.repository;

import com.shopMeta.auth_service.entity.Role;
import com.shopMeta.auth_service.entity.Enums.RoleName;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}