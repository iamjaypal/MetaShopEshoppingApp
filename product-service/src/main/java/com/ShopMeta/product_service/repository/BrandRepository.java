package com.ShopMeta.product_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ShopMeta.product_service.Entity.Brand;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findBySlug(String slug);

    List<Brand> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);
}
