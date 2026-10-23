package com.ShopMeta.product_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ShopMeta.product_service.dto.ApiResponse;
import com.ShopMeta.product_service.dto.ProductPageResponse;
import com.ShopMeta.product_service.dto.ProductResponse;
import com.ShopMeta.product_service.service.ProductService;

/**
 * Public product catalog endpoints (read-only, no authentication required).
 */
@Tag(name = "Products (public)", description = "Public catalog search, filters and product detail")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Catalog search / filter / sort / pagination (spec section 8.3).
     */
    @Operation(summary = "Search / filter / sort / paginate products",
            description = "Only active products. size is clamped to 1..100; sort is whitelisted.")
    @GetMapping
    public ResponseEntity<ApiResponse<ProductPageResponse>> getAllProducts(

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "20") int size,

            @RequestParam(defaultValue = "createdAt,desc") String sort,

            @RequestParam(required = false) String q,

            @RequestParam(required = false) String category,

            @RequestParam(required = false) String brand,

            @RequestParam(required = false) BigDecimal minPrice,

            @RequestParam(required = false) BigDecimal maxPrice) {

        ProductPageResponse response = productService.getAllProducts(
                page,
                size,
                sort,
                q,
                category,
                brand,
                minPrice,
                maxPrice);

        return ResponseEntity.ok(
                ApiResponse.<ProductPageResponse>builder()
                        .success(true)
                        .message("Products fetched successfully")
                        .data(response)
                        .build());
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProductResponse> getProductBySlug(
            @PathVariable String slug) {

        return ResponseEntity.ok(productService.getProductBySlug(slug));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long id) {

        return ResponseEntity.ok(productService.getProductById(id));
    }
}
