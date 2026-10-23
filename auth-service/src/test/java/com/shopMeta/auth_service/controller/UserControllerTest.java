package com.shopMeta.auth_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
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

import com.shopMeta.auth_service.dto.UserSummary;
import com.shopMeta.auth_service.entity.Role;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.entity.Enums.RoleName;
import com.shopMeta.auth_service.exception.BusinessRuleException;
import com.shopMeta.auth_service.security.CustomUserDetails;
import com.shopMeta.auth_service.service.UserService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @BeforeEach
    void authenticate() {

        User user = User.builder()
                .id(1L)
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .enabled(true)
                .emailVerified(false)
                .roles(new HashSet<>(Set.of(
                        Role.builder().id(1L).name(RoleName.CUSTOMER).build())))
                .build();

        CustomUserDetails principal = new CustomUserDetails(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getProfile_returnsTheAuthenticatedUser() throws Exception {

        given(userService.getProfile(1L)).willReturn(UserSummary.builder()
                .id(1L).firstName("Test").lastName("User")
                .email("test@example.com").roles(Set.of("CUSTOMER")).build());

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("CUSTOMER"));
    }

    @Test
    void updateProfile_returnsUpdatedUser() throws Exception {

        given(userService.updateProfile(eq(1L), any())).willReturn(UserSummary.builder()
                .id(1L).firstName("Updated").lastName("User")
                .email("test@example.com").phone("+919000000002").build());

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Updated", "lastName": "User", "phone": "+919000000002"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated successfully"))
                .andExpect(jsonPath("$.data.firstName").value("Updated"));
    }

    @Test
    void updateProfile_invalidBody_returnsBadRequest() throws Exception {

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "", "lastName": "User", "phone": "12345"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.firstName").exists())
                .andExpect(jsonPath("$.data.phone").exists());
    }

    @Test
    void changePassword_wrongCurrent_returnsUnprocessable() throws Exception {

        willThrow(new BusinessRuleException("Current password is incorrect"))
                .given(userService).changePassword(eq(1L), any());

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "wrong", "newPassword": "N3w@Passw0rd"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void changePassword_weakNewPassword_returnsBadRequest() throws Exception {

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Str0ng@Pass", "newPassword": "weak"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.newPassword").exists());
    }
}
