package com.shopMeta.inventory_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shopMeta.inventory_service.config.InternalApiProperties;

class InternalTokenFilterTest {

    private static final String TOKEN = "test-internal-token";

    private InternalTokenFilter filter;

    @BeforeEach
    void setUp() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setToken(TOKEN);
        filter = new InternalTokenFilter(properties);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain invoke(String uri, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        if (token != null) {
            request.addHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER, token);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
        return chain;
    }

    @Test
    @DisplayName("a valid token lets an internal call through and marks it internal")
    void validTokenPassesThrough() throws Exception {

        MockFilterChain chain = invoke("/api/v1/internal/inventory/reserve", TOKEN);

        assertThat(chain.getRequest()).isNotNull();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly(InternalTokenFilter.INTERNAL_AUTHORITY);
    }

    @Test
    @DisplayName("a missing token is rejected with 403")
    void missingTokenIsForbidden() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/internal/inventory/reserve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("X-Internal-Token");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("a wrong token is rejected with 403")
    void wrongTokenIsForbidden() throws Exception {

        MockFilterChain chain = invoke("/api/v1/internal/inventory/reserve", "not-the-token");

        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("public paths are untouched by the internal token check")
    void publicPathNeedsNoToken() throws Exception {

        MockFilterChain chain = invoke("/api/v1/inventory/1", null);

        assertThat(chain.getRequest()).isNotNull();
    }
}
