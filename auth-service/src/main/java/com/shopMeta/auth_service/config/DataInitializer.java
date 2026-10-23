package com.shopMeta.auth_service.config;

import com.shopMeta.auth_service.entity.Role;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.entity.Enums.RoleName;
import com.shopMeta.auth_service.repository.RoleRepository;
import com.shopMeta.auth_service.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Seeds reference data.
 *
 * <ul>
 *   <li>{@code CUSTOMER} and {@code ADMIN} roles are created in <b>every</b> environment
 *       (spec 7.2) — registration depends on them.</li>
 *   <li>The demo {@code admin@metashop.com} and {@code customer@metashop.com} accounts
 *       (spec 7.2 / 20) are created outside the {@code prod} profile only. Passwords come from
 *       the {@code ADMIN_PASSWORD} / {@code CUSTOMER_PASSWORD} env vars.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${metashop.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${metashop.seed.admin.email:admin@metashop.com}")
    private String adminEmail;

    @Value("${metashop.seed.admin.password:Admin@12345}")
    private String adminPassword;

    @Value("${metashop.seed.customer.email:customer@metashop.com}")
    private String customerEmail;

    @Value("${metashop.seed.customer.password:Customer@12345}")
    private String customerPassword;

    @Override
    @Transactional
    public void run(String... args) {

        Role customerRole = ensureRole(RoleName.CUSTOMER);
        Role adminRole = ensureRole(RoleName.ADMIN);

        log.info("Roles initialized: CUSTOMER, ADMIN");

        if (!seedEnabled) {
            return;
        }

        seedUser(adminEmail, adminPassword, "MetaShop", "Admin", Set.of(adminRole), true);
        seedUser(customerEmail, customerPassword, "MetaShop", "Customer", Set.of(customerRole), true);
    }

    private Role ensureRole(RoleName name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(Role.builder().name(name).build()));
    }

    private void seedUser(String email, String rawPassword, String firstName, String lastName,
                          Set<Role> roles, boolean emailVerified) {

        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }

        userRepository.save(User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .enabled(true)
                .emailVerified(emailVerified)
                .roles(roles)
                .build());

        log.info("Seeded demo account {}", normalizedEmail);
    }
}
