package com.ShopMeta.product_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import com.ShopMeta.product_service.dto.ProductImageResponse;
import com.ShopMeta.product_service.dto.ProductPageResponse;
import com.ShopMeta.product_service.dto.ProductRequest;
import com.ShopMeta.product_service.dto.ProductResponse;
import com.ShopMeta.product_service.dto.ProductSummaryResponse;
import com.ShopMeta.product_service.service.ProductService;

@WebMvcTest(AdminProductController.class)
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void getAllProducts_returnsPaginatedResult() throws Exception {

        ProductSummaryResponse summary = ProductSummaryResponse.builder()
                .id(1L).name("iPhone 15").slug("iphone-15").active(false).build();

        given(productService.getAllProductsForAdmin(
                anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(ProductPageResponse.builder()
                        .content(List.of(summary)).page(0).size(20)
                        .totalElements(1).totalPages(1).last(true).build());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/admin/products").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].active").value(false));
    }

    @Test
    void createProduct_returns201WithLocationHeader() throws Exception {

        given(productService.createProduct(any(ProductRequest.class)))
                .willReturn(ProductResponse.builder()
                        .id(3L).name("iPhone 15").slug("iphone-15")
                        .price(new BigDecimal("79999.00")).active(true).build());

        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"iPhone 15","sku":"SKU-1","price":79999.00,
                                 "mrp":89999.00,"categoryId":1,"brandId":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith("/api/v1/admin/products/3")))
                .andExpect(jsonPath("$.slug").value("iphone-15"));
    }

    @Test
    void updateProductStatus_returns200() throws Exception {

        given(productService.updateProductStatus(eq(3L), eq(false)))
                .willReturn(ProductResponse.builder().id(3L).active(false).build());

        mockMvc.perform(patch("/api/v1/admin/products/3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void uploadImage_returns201() throws Exception {

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] { 1, 2, 3 });

        given(productService.addImage(eq(3L), any(MultipartFile.class), eq("front"), anyBoolean()))
                .willReturn(ProductImageResponse.builder()
                        .id(9L).url("/uploads/products/abc.png").primary(true).build());

        mockMvc.perform(multipart("/api/v1/admin/products/3/images")
                        .file(file)
                        .param("altText", "front")
                        .param("primary", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("/uploads/products/abc.png"));
    }

    @Test
    void deleteImage_returns204() throws Exception {

        mockMvc.perform(delete("/api/v1/admin/products/3/images/9"))
                .andExpect(status().isNoContent());

        verify(productService).deleteImage(3L, 9L);
    }
}
