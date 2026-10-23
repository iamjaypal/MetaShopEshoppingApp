package com.ShopMeta.product_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ShopMeta.product_service.Entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);

    Optional<Category> findBySlug(String slug);

    List<Category> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    boolean existsByParentId(Long parentId);
}
