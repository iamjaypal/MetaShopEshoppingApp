package com.shopMeta.inventory_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shopMeta.inventory_service.dto.InventoryPageResponse;
import com.shopMeta.inventory_service.dto.InventoryResponse;
import com.shopMeta.inventory_service.dto.StockCreateRequest;
import com.shopMeta.inventory_service.dto.StockUpdateRequest;
import com.shopMeta.inventory_service.exception.BusinessRuleException;
import com.shopMeta.inventory_service.exception.InvalidRequestException;
import com.shopMeta.inventory_service.service.InventoryService;

@WebMvcTest(AdminInventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    private InventoryResponse response(int available, int threshold) {
        return InventoryResponse.builder()
                .productId(1L).sku("SKU-1")
                .availableQuantity(available).reservedQuantity(0)
                .lowStockThreshold(threshold)
                .lowStock(available <= threshold)
                .inStock(available > 0)
                .build();
    }

    @Test
    void createStock_returnsCreatedWithLocation() throws Exception {

        given(inventoryService.createStock(any(StockCreateRequest.class), any()))
                .willReturn(response(25, 5));

        mockMvc.perform(post("/api/v1/admin/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"sku":"SKU-1","quantity":25}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/inventory/1"))
                .andExpect(jsonPath("$.availableQuantity").value(25))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void createStock_missingQuantity_isBadRequest() throws Exception {

        mockMvc.perform(post("/api/v1/admin/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.quantity").exists());
    }

    @Test
    void createStock_negativeQuantity_isBadRequest() throws Exception {

        mockMvc.perform(post("/api/v1/admin/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"quantity":-5}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createStock_duplicateProduct_isUnprocessable() throws Exception {

        willThrow(new BusinessRuleException("Inventory already exists for product 1"))
                .given(inventoryService).createStock(any(StockCreateRequest.class), any());

        mockMvc.perform(post("/api/v1/admin/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"quantity":5}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Inventory already exists for product 1"));
    }

    @Test
    void getStock_returnsFullView() throws Exception {

        given(inventoryService.getStock(1L)).willReturn(response(10, 5));

        mockMvc.perform(get("/api/v1/admin/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.reservedQuantity").value(0))
                .andExpect(jsonPath("$.lowStockThreshold").value(5));
    }

    @Test
    void updateStock_withQuantity_returnsUpdatedRow() throws Exception {

        given(inventoryService.updateStock(eq(1L), any(StockUpdateRequest.class), any()))
                .willReturn(response(40, 5));

        mockMvc.perform(put("/api/v1/admin/inventory/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":40}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(40));
    }

    @Test
    void updateStock_ambiguousBody_isBadRequest() throws Exception {

        willThrow(new InvalidRequestException(
                "Provide exactly one of 'quantity' (set the absolute value) "
                        + "or 'adjustment' (a signed delta)"))
                .given(inventoryService).updateStock(eq(1L), any(StockUpdateRequest.class), any());

        mockMvc.perform(put("/api/v1/admin/inventory/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":5,"adjustment":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void findLowStock_returnsPagedEnvelope() throws Exception {

        given(inventoryService.findLowStock(any(), eq(0), eq(20)))
                .willReturn(InventoryPageResponse.builder()
                        .content(List.of(response(2, 5)))
                        .page(0).size(20).totalElements(1).totalPages(1).last(true)
                        .build());

        mockMvc.perform(get("/api/v1/admin/inventory/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productId").value(1))
                .andExpect(jsonPath("$.content[0].lowStock").value(true))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
