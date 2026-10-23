package com.ShopMeta.product_service.service;

import com.ShopMeta.product_service.Entity.Category;
import com.ShopMeta.product_service.dto.CategoryRequest;
import com.ShopMeta.product_service.dto.CategoryResponse;
import com.ShopMeta.product_service.exception.BusinessRuleException;
import com.ShopMeta.product_service.exception.DuplicateResourceException;
import com.ShopMeta.product_service.exception.ResourceNotFoundException;
import com.ShopMeta.product_service.repository.CategoryRepository;
import com.ShopMeta.product_service.util.SlugUtils;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    private CategoryResponse toResponse(Category category) {

        Category parent = category.getParent();

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .parentId(parent != null ? parent.getId() : null)
                .parentName(parent != null ? parent.getName() : null)
                .active(category.getActive())
                .children(new ArrayList<>())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    private String resolveSlug(String providedSlug, String name) {
        if (providedSlug != null && !providedSlug.isBlank()) {
            return SlugUtils.toSlug(providedSlug);
        }
        return SlugUtils.toSlug(name);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    private Category resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent category not found with id: " + parentId));
    }

    private void validateNoCycle(Category category, Category parent) {

        Category current = parent;

        while (current != null) {
            if (current.getId() != null && current.getId().equals(category.getId())) {
                throw new BusinessRuleException("Category hierarchy cannot contain a cycle");
            }
            current = current.getParent();
        }
    }

    /**
     * Public category tree. Returns only active categories, nested by parent, ordered by name.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoryTree() {

        List<Category> activeCategories = categoryRepository.findByActiveTrueOrderByNameAsc();

        Map<Long, CategoryResponse> byId = new HashMap<>();

        for (Category category : activeCategories) {
            byId.put(category.getId(), toResponse(category));
        }

        List<CategoryResponse> roots = new ArrayList<>();

        for (Category category : activeCategories) {

            CategoryResponse node = byId.get(category.getId());

            Category parent = category.getParent();

            if (parent != null && byId.containsKey(parent.getId())) {
                byId.get(parent.getId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }

        return roots;
    }

    /**
     * Admin category list. Returns all categories (including inactive) as a flat list.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {

        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {

        return toResponse(findCategory(id));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {

        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Category already exists with name: " + request.getName());
        }

        String slug = resolveSlug(request.getSlug(), request.getName());

        if (categoryRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Category already exists with slug: " + slug);
        }

        Category parent = resolveParent(request.getParentId());

        Category category = Category.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .parent(parent)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {

        Category category = findCategory(id);

        if (!category.getName().equalsIgnoreCase(request.getName())
                && categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Category already exists with name: " + request.getName());
        }

        String slug = resolveSlug(request.getSlug(), request.getName());

        if (!slug.equals(category.getSlug()) && categoryRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Category already exists with slug: " + slug);
        }

        Category parent = resolveParent(request.getParentId());

        validateNoCycle(category, parent);

        category.setName(request.getName());
        category.setSlug(slug);
        category.setDescription(request.getDescription());
        category.setParent(parent);

        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }

        return toResponse(categoryRepository.save(category));
    }

    /**
     * Soft delete. Refused while the category still has sub-categories.
     */
    @Transactional
    public void deleteCategory(Long id) {

        Category category = findCategory(id);

        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessRuleException("Cannot delete a category that has sub-categories");
        }

        category.setActive(false);

        categoryRepository.save(category);
    }
}
