package com.shopMeta.inventory_service.controller;

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

import com.shopMeta.inventory_service.dto.AvailabilityResponse;
import com.shopMeta.inventory_service.exception.ResourceNotFoundException;
import com.shopMeta.inventory_service.service.InventoryService;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @Test
    void getAvailability_returnsAvailability() throws Exception {

        given(inventoryService.getAvailability(1L)).willReturn(AvailabilityResponse.builder()
                .productId(1L).inStock(true).availableQuantity(3).build());

        mockMvc.perform(get("/api/v1/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.inStock").value(true))
                .andExpect(jsonPath("$.availableQuantity").value(3))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void getAvailability_unknownProduct_returnsNotFound() throws Exception {

        willThrow(new ResourceNotFoundException("No inventory found for product 99"))
                .given(inventoryService).getAvailability(99L);

        mockMvc.perform(get("/api/v1/inventory/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
