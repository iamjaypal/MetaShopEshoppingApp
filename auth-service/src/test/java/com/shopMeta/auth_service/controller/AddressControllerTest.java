package com.shopMeta.auth_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.shopMeta.auth_service.dto.AddressResponse;
import com.shopMeta.auth_service.entity.Role;
import com.shopMeta.auth_service.entity.User;
import com.shopMeta.auth_service.entity.Enums.RoleName;
import com.shopMeta.auth_service.exception.BusinessRuleException;
import com.shopMeta.auth_service.exception.ResourceNotFoundException;
import com.shopMeta.auth_service.security.CustomUserDetails;
import com.shopMeta.auth_service.service.AddressService;

@WebMvcTest(AddressController.class)
@AutoConfigureMockMvc(addFilters = false)
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    @BeforeEach
    void authenticate() {

        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .enabled(true)
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

    private AddressResponse address(long id, boolean isDefault) {
        return AddressResponse.builder()
                .id(id).fullName("Test User").phone("+919000000001")
                .line1("1 Main St").city("Pune").state("MH")
                .postalCode("411001").country("IN").isDefault(isDefault)
                .build();
    }

    @Test
    void addAddress_returnsCreated() throws Exception {

        given(addressService.create(eq(1L), any())).willReturn(address(1L, true));

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Test User", "phone": "+919000000001", "line1": "1 Main St",
                                 "city": "Pune", "state": "MH", "postalCode": "411001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    void addAddress_invalidBody_returnsBadRequest() throws Exception {

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "", "phone": "bad", "line1": "", "city": "", "state": "", "postalCode": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fullName").exists())
                .andExpect(jsonPath("$.data.phone").exists());
    }

    @Test
    void addAddress_beyondLimit_returnsUnprocessable() throws Exception {

        willThrow(new BusinessRuleException("A user can have at most 10 addresses"))
                .given(addressService).create(eq(1L), any());

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Test User", "phone": "+919000000001", "line1": "1 Main St",
                                 "city": "Pune", "state": "MH", "postalCode": "411001"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("A user can have at most 10 addresses"));
    }

    @Test
    void listAddresses_returnsThem() throws Exception {

        given(addressService.list(1L)).willReturn(List.of(address(1L, true), address(2L, false)));

        mockMvc.perform(get("/api/v1/users/me/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    void setDefault_returnsTheUpdatedAddress() throws Exception {

        given(addressService.setDefault(1L, 2L)).willReturn(address(2L, true));

        mockMvc.perform(patch("/api/v1/users/me/addresses/2/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    void deleteAddress_returnsNoContent() throws Exception {

        mockMvc.perform(delete("/api/v1/users/me/addresses/2"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getAddress_notOwned_returnsNotFound() throws Exception {

        given(addressService.get(1L, 99L))
                .willThrow(new ResourceNotFoundException("Address not found"));

        mockMvc.perform(get("/api/v1/users/me/addresses/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Address not found"));
    }
}
