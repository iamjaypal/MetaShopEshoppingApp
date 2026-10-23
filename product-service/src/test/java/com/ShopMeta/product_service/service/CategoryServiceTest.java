package com.ShopMeta.product_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ShopMeta.product_service.Entity.Category;
import com.ShopMeta.product_service.dto.CategoryRequest;
import com.ShopMeta.product_service.dto.CategoryResponse;
import com.ShopMeta.product_service.exception.BusinessRuleException;
import com.ShopMeta.product_service.exception.DuplicateResourceException;
import com.ShopMeta.product_service.exception.ResourceNotFoundException;
import com.ShopMeta.product_service.repository.CategoryRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private CategoryRequest request(String name) {
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        return request;
    }

    @Test
    void createCategory_generatesSlugAndDefaultsActiveTrue() {

        given(categoryRepository.existsByNameIgnoreCase("Electronics")).willReturn(false);
        given(categoryRepository.existsBySlug("electronics")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(1L);
            return category;
        });

        CategoryResponse response = categoryService.createCategory(request("Electronics"));

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getSlug()).isEqualTo("electronics");
        assertThat(response.getActive()).isTrue();
        assertThat(response.getChildren()).isEmpty();
    }

    @Test
    void createCategory_duplicateName_throwsDuplicateResourceException() {

        given(categoryRepository.existsByNameIgnoreCase("Electronics")).willReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request("Electronics")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createCategory_parentNotFound_throwsResourceNotFoundException() {

        CategoryRequest request = request("Phones");
        request.setParentId(99L);

        given(categoryRepository.existsByNameIgnoreCase("Phones")).willReturn(false);
        given(categoryRepository.existsBySlug("phones")).willReturn(false);
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCategory_selfParent_throwsBusinessRuleException() {

        Category category = Category.builder().id(1L).name("Electronics").slug("electronics").active(true).build();

        CategoryRequest request = request("Electronics");
        request.setParentId(1L);

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.updateCategory(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void updateCategory_parentIsDescendant_throwsBusinessRuleException() {

        Category parent = Category.builder().id(1L).name("Electronics").slug("electronics").active(true).build();
        Category child = Category.builder().id(2L).name("Phones").slug("phones").parent(parent).active(true).build();

        CategoryRequest request = request("Electronics");
        request.setParentId(2L);

        given(categoryRepository.findById(1L)).willReturn(Optional.of(parent));
        given(categoryRepository.findById(2L)).willReturn(Optional.of(child));

        assertThatThrownBy(() -> categoryService.updateCategory(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void deleteCategory_withChildren_throwsBusinessRuleException() {

        Category category = Category.builder().id(1L).name("Electronics").slug("electronics").active(true).build();

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(categoryRepository.existsByParentId(1L)).willReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(BusinessRuleException.class);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deleteCategory_softDeletes() {

        Category category = Category.builder().id(1L).name("Electronics").slug("electronics").active(true).build();

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(categoryRepository.existsByParentId(1L)).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> invocation.getArgument(0));

        categoryService.deleteCategory(1L);

        assertThat(category.getActive()).isFalse();
    }

    @Test
    void getCategoryTree_buildsNestedHierarchy() {

        Category parent = Category.builder().id(1L).name("Electronics").slug("electronics").active(true).build();
        Category child = Category.builder().id(2L).name("Phones").slug("phones").parent(parent).active(true).build();

        given(categoryRepository.findByActiveTrueOrderByNameAsc()).willReturn(List.of(parent, child));

        List<CategoryResponse> tree = categoryService.getCategoryTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getName()).isEqualTo("Electronics");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getName()).isEqualTo("Phones");
        assertThat(tree.get(0).getChildren().get(0).getParentId()).isEqualTo(1L);
    }
}
