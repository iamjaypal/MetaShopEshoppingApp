package com.ShopMeta.product_service.repository;

import com.ShopMeta.product_service.Entity.Product;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic filters for the product query (spec section 8.3):
 * q, category (slug), brand (slug), minPrice, maxPrice and an optional active flag.
 */
public class ProductSpecification {

    private ProductSpecification() {
    }

    /**
     * Public catalog filter: always restricted to active products.
     */
    public static Specification<Product> filterProducts(
            String q,
            String categorySlug,
            String brandSlug,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        return filterProducts(q, categorySlug, brandSlug, minPrice, maxPrice, Boolean.TRUE);
    }

    /**
     * Admin filter: {@code active == null} means "any status".
     */
    public static Specification<Product> filterProducts(
            String q,
            String categorySlug,
            String brandSlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean active
    ) {

        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            // Category filter by slug
            if (categorySlug != null && !categorySlug.isBlank()) {
                Join<Product, ?> categoryJoin = root.join("category", JoinType.LEFT);
                predicates.add(criteriaBuilder.equal(categoryJoin.get("slug"), categorySlug.trim()));
            }

            // Brand filter by slug
            if (brandSlug != null && !brandSlug.isBlank()) {
                Join<Product, ?> brandJoin = root.join("brand", JoinType.LEFT);
                predicates.add(criteriaBuilder.equal(brandJoin.get("slug"), brandSlug.trim()));
            }

            // Minimum price
            if (minPrice != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice));
            }

            // Maximum price
            if (maxPrice != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            // Keyword search across name / short description / description / sku / brand name
            if (q != null && !q.trim().isEmpty()) {

                String searchKeyword = "%" + q.trim().toLowerCase() + "%";

                Join<Product, ?> brandJoin = root.join("brand", JoinType.LEFT);

                predicates.add(
                        criteriaBuilder.or(
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), searchKeyword),
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("shortDescription")), searchKeyword),
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), searchKeyword),
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), searchKeyword),
                                criteriaBuilder.like(criteriaBuilder.lower(brandJoin.get("name")), searchKeyword)
                        )
                );
            }

            // Optional active-status restriction
            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
