package com.shopMeta.auth_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shopMeta.auth_service.dto.AdminUserResponse;
import com.shopMeta.auth_service.dto.PageResponse;
import com.shopMeta.auth_service.entity.Role;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.entity.Enums.RoleName;
import com.shopMeta.auth_service.exception.BusinessRuleException;
import com.shopMeta.auth_service.exception.InvalidRequestException;
import com.shopMeta.auth_service.security.CustomUserDetails;
import com.shopMeta.auth_service.service.AdminUserService;

@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @BeforeEach
    void authenticate() {

        User admin = User.builder()
                .id(1L)
                .email("admin@metashop.com")
                .enabled(true)
                .roles(new HashSet<>(Set.of(
                        Role.builder().id(2L).name(RoleName.ADMIN).build())))
                .build();

        CustomUserDetails principal = new CustomUserDetails(admin);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private AdminUserResponse adminUser(long id, boolean enabled) {
        return AdminUserResponse.builder()
                .id(id).firstName("Test").lastName("User")
                .email("user" + id + "@example.com")
                .enabled(enabled).emailVerified(true)
                .roles(Set.of("CUSTOMER"))
                .build();
    }

    @Test
    void listUsers_returnsPage() throws Exception {

        given(adminUserService.listUsers(eq(null), eq("CUSTOMER"), eq(null), eq(0), eq(20)))
                .willReturn(new PageResponse<>(List.of(adminUser(2L, true)), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/admin/users").param("role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].email").value("user2@example.com"))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void listUsers_unknownRole_returnsBadRequest() throws Exception {

        willThrow(new InvalidRequestException("Unknown role 'SUPERUSER'. Allowed values: CUSTOMER, ADMIN"))
                .given(adminUserService).listUsers(any(), eq("SUPERUSER"), any(), anyInt(), anyInt());

        mockMvc.perform(get("/api/v1/admin/users").param("role", "SUPERUSER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown role 'SUPERUSER'. Allowed values: CUSTOMER, ADMIN"));
    }

    @Test
    void updateStatus_disablesUser() throws Exception {

        given(adminUserService.updateStatus(eq(1L), eq(2L), eq(false)))
                .willReturn(adminUser(2L, false));

        mockMvc.perform(patch("/api/v1/admin/users/2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User disabled successfully"))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void updateStatus_disablingSelf_returnsUnprocessable() throws Exception {

        willThrow(new BusinessRuleException("You cannot disable your own account"))
                .given(adminUserService).updateStatus(eq(1L), eq(1L), eq(false));

        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You cannot disable your own account"));
    }

    @Test
    void updateStatus_missingEnabled_returnsBadRequest() throws Exception {

        mockMvc.perform(patch("/api/v1/admin/users/2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.enabled").exists());
    }
}
