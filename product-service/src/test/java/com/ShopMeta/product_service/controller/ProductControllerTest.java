package com.ShopMeta.product_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ShopMeta.product_service.dto.ProductPageResponse;
import com.ShopMeta.product_service.dto.ProductResponse;
import com.ShopMeta.product_service.dto.ProductSummaryResponse;
import com.ShopMeta.product_service.exception.InvalidRequestException;
import com.ShopMeta.product_service.exception.ResourceNotFoundException;
import com.ShopMeta.product_service.service.ProductService;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void getAllProducts_returnsPaginatedResult() throws Exception {

        ProductSummaryResponse summary = ProductSummaryResponse.builder()
                .id(1L).name("iPhone 15").slug("iphone-15")
                .price(new BigDecimal("79999.00")).active(true).build();

        given(productService.getAllProducts(
                anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                .willReturn(ProductPageResponse.builder()
                        .content(List.of(summary))
                        .page(0).size(20).totalElements(1).totalPages(1).last(true)
                        .build());

        mockMvc.perform(get("/api/v1/products").param("q", "iphone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].slug").value("iphone-15"))
                .andExpect(jsonPath("$.data.last").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getAllProducts_invalidSort_returns400() throws Exception {

        given(productService.getAllProducts(
                anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                .willThrow(new InvalidRequestException("Invalid sort field 'hacker'"));

        mockMvc.perform(get("/api/v1/products").param("sort", "hacker,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getProductById_returns200() throws Exception {

        given(productService.getProductById(1L))
                .willReturn(ProductResponse.builder().id(1L).name("iPhone 15").slug("iphone-15").build());

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("iphone-15"));
    }

    @Test
    void getProductById_notFound_returns404() throws Exception {

        given(productService.getProductById(99L))
                .willThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/v1/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getProductBySlug_returns200() throws Exception {

        given(productService.getProductBySlug(eq("iphone-15")))
                .willReturn(ProductResponse.builder().id(1L).name("iPhone 15").slug("iphone-15").build());

        mockMvc.perform(get("/api/v1/products/slug/iphone-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("iphone-15"));
    }
}
