package com.shopMeta.auth_service.config;

import com.shopMeta.auth_service.entity.Role;
import com.shopMeta.auth_service.entity.Enums.RoleName;
import com.shopMeta.auth_service.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {

        if (roleRepository.findByName(RoleName.CUSTOMER).isEmpty()) {

            roleRepository.save(
                    Role.builder()
                            .name(RoleName.CUSTOMER)
                            .build()
            );
        }

        if (roleRepository.findByName(RoleName.ADMIN).isEmpty()) {

            roleRepository.save(
                    Role.builder()
                            .name(RoleName.ADMIN)
                            .build()
            );
        }

        System.out.println("Default roles initialized successfully.");
    }
}
