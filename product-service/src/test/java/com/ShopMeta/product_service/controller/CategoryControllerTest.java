package com.ShopMeta.product_service.controller;

import static org.mockito.ArgumentMatchers.any;
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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ShopMeta.product_service.dto.CategoryRequest;
import com.ShopMeta.product_service.dto.CategoryResponse;
import com.ShopMeta.product_service.exception.BusinessRuleException;
import com.ShopMeta.product_service.exception.ResourceNotFoundException;
import com.ShopMeta.product_service.service.CategoryService;

@WebMvcTest({ CategoryController.class, AdminCategoryController.class })
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void getCategoryTree_returnsOkWithNestedChildren() throws Exception {

        CategoryResponse child = CategoryResponse.builder()
                .id(2L).name("Phones").slug("phones").parentId(1L).active(true).build();

        CategoryResponse root = CategoryResponse.builder()
                .id(1L).name("Electronics").slug("electronics").active(true)
                .children(List.of(child))
                .build();

        given(categoryService.getCategoryTree()).willReturn(List.of(root));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Electronics"))
                .andExpect(jsonPath("$.data[0].children[0].name").value("Phones"));
    }

    @Test
    void getCategoryById_notFound_returns404() throws Exception {

        given(categoryService.getCategoryById(99L))
                .willThrow(new ResourceNotFoundException("Category not found with id: 99"));

        mockMvc.perform(get("/api/v1/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createCategory_returns201WithLocationHeader() throws Exception {

        given(categoryService.createCategory(any(CategoryRequest.class)))
                .willReturn(CategoryResponse.builder()
                        .id(7L).name("Books").slug("books").active(true).build());

        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Books\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith("/api/v1/admin/categories/7")))
                .andExpect(jsonPath("$.slug").value("books"));
    }

    @Test
    void createCategory_invalidBody_returns400() throws Exception {

        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(categoryService, never()).createCategory(any(CategoryRequest.class));
    }

    @Test
    void deleteCategory_withChildren_returns422() throws Exception {

        org.mockito.BDDMockito.willThrow(new BusinessRuleException("Cannot delete a category that has sub-categories"))
                .given(categoryService).deleteCategory(1L);

        mockMvc.perform(delete("/api/v1/admin/categories/1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }
}
