package com.ShopMeta.product_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ShopMeta.product_service.dto.InternalProductResponse;
import com.ShopMeta.product_service.exception.ResourceNotFoundException;
import com.ShopMeta.product_service.service.ProductService;

@WebMvcTest(InternalProductController.class)
class InternalProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void getProductsBatch_returnsProductSnapshots() throws Exception {

        given(productService.getProductsByIds(any()))
                .willReturn(List.of(InternalProductResponse.builder()
                        .id(1L).sku("SKU-1").name("iPhone 15")
                        .price(new BigDecimal("79999.00")).active(true).build()));

        mockMvc.perform(post("/api/v1/internal/products/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$[0].price").value(79999.00));
    }

    @Test
    void getProductsBatch_emptyIds_returns400() throws Exception {

        mockMvc.perform(post("/api/v1/internal/products/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(productService, never()).getProductsByIds(any());
    }

    @Test
    void getProductById_returnsSnapshot() throws Exception {

        given(productService.getInternalProductById(1L))
                .willReturn(InternalProductResponse.builder()
                        .id(1L).sku("SKU-1").name("iPhone 15").active(false).build());

        mockMvc.perform(get("/api/v1/internal/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void getProductById_notFound_returns404() throws Exception {

        given(productService.getInternalProductById(99L))
                .willThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/v1/internal/products/99"))
                .andExpect(status().isNotFound());
    }
}
