package com.shopMeta.auth_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shopMeta.auth_service.dto.LoginResponse;
import com.shopMeta.auth_service.dto.RegisterResponse;
import com.shopMeta.auth_service.dto.UserSummary;
import com.shopMeta.auth_service.exception.EmailAlreadyExistsException;
import com.shopMeta.auth_service.exception.InvalidCredentialsException;
import com.shopMeta.auth_service.service.AuthService;
import com.shopMeta.auth_service.service.PasswordResetService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    private static final String REGISTER_BODY = """
            {
              "firstName": "Jay",
              "lastName": "Pal",
              "email": "jay@example.com",
              "phone": "+919876543210",
              "password": "Str0ng@Pass"
            }
            """;

    @Test
    void register_returnsCreated() throws Exception {

        given(authService.register(any())).willReturn(RegisterResponse.builder()
                .id(1L).firstName("Jay").lastName("Pal")
                .email("jay@example.com").phone("+919876543210").build());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User Registered Successfully"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("jay@example.com"));
    }

    @Test
    void register_invalidBody_returnsValidationErrors() throws Exception {

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "", "lastName": "Pal", "email": "not-an-email", "password": "weak"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation Failed"))
                .andExpect(jsonPath("$.data.firstName").exists())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    void register_duplicateEmail_returnsConflict() throws Exception {

        willThrow(new EmailAlreadyExistsException("Email is already registered"))
                .given(authService).register(any());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void login_returnsTokenPair() throws Exception {

        given(authService.login(any())).willReturn(LoginResponse.builder()
                .accessToken("access").refreshToken("refresh").tokenType("Bearer")
                .expiresIn(900L)
                .user(UserSummary.builder().id(1L).email("jay@example.com")
                        .roles(Set.of("CUSTOMER")).build())
                .build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jay@example.com", "password": "Str0ng@Pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.user.roles[0]").value("CUSTOMER"));
    }

    @Test
    void login_badCredentials_returnsUnauthorized() throws Exception {

        willThrow(new InvalidCredentialsException("Invalid email or password"))
                .given(authService).login(any());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jay@example.com", "password": "wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void refresh_returnsNewPair() throws Exception {

        given(authService.refresh("old")).willReturn(LoginResponse.builder()
                .accessToken("access2").refreshToken("refresh2").tokenType("Bearer")
                .expiresIn(900L).build());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"old\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access2"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh2"));
    }

    @Test
    void refresh_missingToken_returnsBadRequest() throws Exception {

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    void forgotPassword_alwaysReturnsOk() throws Exception {

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"jay@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void resetPassword_returnsOk() throws Exception {

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"abc\", \"newPassword\": \"N3w@Passw0rd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully"));
    }
}
