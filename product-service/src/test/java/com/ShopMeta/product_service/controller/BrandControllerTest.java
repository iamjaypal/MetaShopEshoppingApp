package com.ShopMeta.product_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ShopMeta.product_service.dto.BrandRequest;
import com.ShopMeta.product_service.dto.BrandResponse;
import com.ShopMeta.product_service.exception.DuplicateResourceException;
import com.ShopMeta.product_service.exception.ResourceNotFoundException;
import com.ShopMeta.product_service.service.BrandService;

@WebMvcTest({ BrandController.class, AdminBrandController.class })
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandService brandService;

    @Test
    void getBrands_returnsOkWithData() throws Exception {

        given(brandService.getActiveBrands())
                .willReturn(List.of(BrandResponse.builder()
                        .id(1L).name("Samsung").slug("samsung").active(true).build()));

        mockMvc.perform(get("/api/v1/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Samsung"))
                .andExpect(jsonPath("$.data[0].slug").value("samsung"));
    }

    @Test
    void getBrandById_notFound_returns404() throws Exception {

        given(brandService.getBrandById(99L))
                .willThrow(new ResourceNotFoundException("Brand not found with id: 99"));

        mockMvc.perform(get("/api/v1/brands/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Brand not found with id: 99"));
    }

    @Test
    void createBrand_returns201WithLocationHeader() throws Exception {

        given(brandService.createBrand(any(BrandRequest.class)))
                .willReturn(BrandResponse.builder()
                        .id(5L).name("Apple").slug("apple").active(true).build());

        mockMvc.perform(post("/api/v1/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Apple\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/v1/admin/brands/5")))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.slug").value("apple"));
    }

    @Test
    void createBrand_invalidBody_returns400() throws Exception {

        mockMvc.perform(post("/api/v1/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(brandService, never()).createBrand(any(BrandRequest.class));
    }

    @Test
    void createBrand_duplicateName_returns409() throws Exception {

        given(brandService.createBrand(any(BrandRequest.class)))
                .willThrow(new DuplicateResourceException("Brand already exists with name: Apple"));

        mockMvc.perform(post("/api/v1/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Apple\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deleteBrand_returns204() throws Exception {

        mockMvc.perform(delete("/api/v1/admin/brands/5"))
                .andExpect(status().isNoContent());

        verify(brandService).deleteBrand(eq(5L));
    }
}
