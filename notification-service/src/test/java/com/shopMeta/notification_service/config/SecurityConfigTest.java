package com.shopMeta.notification_service.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopMeta.notification_service.controller.AdminNotificationController;
import com.shopMeta.notification_service.dto.NotificationPageResponse;
import com.shopMeta.notification_service.security.JwtService;
import com.shopMeta.notification_service.service.NotificationService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

/**
 * The authorization rule on the admin surface (spec 13: "APIs (admin only)").
 *
 * <p>Unlike the other controller tests this one runs the real filter chain with real signed tokens,
 * because the thing under test <em>is</em> the chain: an anonymous caller must get 401, a signed-in
 * customer must get 403, and only an ADMIN may read the notification log.
 */
@WebMvcTest(AdminNotificationController.class)
@Import({SecurityConfig.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "metashop.jwt.secret=" + SecurityConfigTest.SECRET,
        "metashop.jwt.issuer=metashop-auth-service"
})
class SecurityConfigTest {

    static final String SECRET = "MetaShopDevOnlyJwtSecretKeyChangeMe0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void anAnonymousRequest_isRejected() throws Exception {

        mockMvc.perform(get("/api/v1/admin/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void aMalformedToken_isRejected() throws Exception {

        mockMvc.perform(get("/api/v1/admin/notifications")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignedWithTheWrongKey_isRejected() throws Exception {

        String forged = Jwts.builder()
                .subject("1")
                .issuer("metashop-auth-service")
                .claim("roles", List.of("ADMIN"))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(
                        "SomeOtherSecretKeyThatIsLongEnoughToBeValid0123456789".getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/v1/admin/notifications")
                        .header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomerToken_isForbidden() throws Exception {

        mockMvc.perform(get("/api/v1/admin/notifications")
                        .header("Authorization", "Bearer " + tokenFor("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void anAdminToken_isAllowed() throws Exception {

        given(notificationService.list(any(), anyInt(), anyInt()))
                .willReturn(NotificationPageResponse.builder().content(List.of()).build());

        mockMvc.perform(get("/api/v1/admin/notifications")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk());
    }

    /**
     * Anything not explicitly permitted is authenticated, so an unknown path under the API is a 401
     * rather than a 404 — the surface is closed by default.
     */
    @Test
    void anUnknownApiPath_stillRequiresAuthentication() throws Exception {

        mockMvc.perform(get("/api/v1/anything-else"))
                .andExpect(status().isUnauthorized());
    }

    private static String tokenFor(String role) {

        return Jwts.builder()
                .subject("1")
                .issuer("metashop-auth-service")
                .claim("email", "admin@metashop.com")
                .claim("roles", List.of(role))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
