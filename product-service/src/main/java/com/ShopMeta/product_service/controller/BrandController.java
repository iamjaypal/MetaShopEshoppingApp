package com.ShopMeta.product_service.controller;

import com.ShopMeta.product_service.dto.ApiResponse;
import com.ShopMeta.product_service.dto.BrandResponse;
import com.ShopMeta.product_service.service.BrandService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public brand endpoints (no authentication required).
 */
@Tag(name = "Brands (public)", description = "Public brand list")
@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getBrands() {

        List<BrandResponse> brands = brandService.getActiveBrands();

        return ResponseEntity.ok(
                ApiResponse.<List<BrandResponse>>builder()
                        .success(true)
                        .message("Brands fetched successfully")
                        .data(brands)
                        .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BrandResponse> getBrandById(@PathVariable Long id) {

        return ResponseEntity.ok(brandService.getBrandById(id));
    }
}
