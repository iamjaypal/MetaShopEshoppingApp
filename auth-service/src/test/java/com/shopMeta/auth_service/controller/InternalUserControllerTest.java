package com.shopMeta.auth_service.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shopMeta.auth_service.dto.AddressResponse;
import com.shopMeta.auth_service.exception.ResourceNotFoundException;
import com.shopMeta.auth_service.service.AddressService;

@WebMvcTest(InternalUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    @Test
    void getAddress_returnsPlainJson() throws Exception {

        given(addressService.get(2L, 1L)).willReturn(AddressResponse.builder()
                .id(1L).fullName("Test User").phone("+919000000001")
                .line1("1 Main St").city("Pune").state("MH")
                .postalCode("411001").country("IN").isDefault(true)
                .build());

        mockMvc.perform(get("/api/v1/internal/users/2/addresses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void getAddress_unknown_returnsNotFound() throws Exception {

        willThrow(new ResourceNotFoundException("Address not found"))
                .given(addressService).get(2L, 99L);

        mockMvc.perform(get("/api/v1/internal/users/2/addresses/99"))
                .andExpect(status().isNotFound());
    }
}
