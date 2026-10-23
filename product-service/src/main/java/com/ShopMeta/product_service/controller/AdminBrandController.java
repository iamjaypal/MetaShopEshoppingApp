package com.ShopMeta.product_service.controller;

import com.ShopMeta.product_service.dto.ApiResponse;
import com.ShopMeta.product_service.dto.BrandRequest;
import com.ShopMeta.product_service.dto.BrandResponse;
import com.ShopMeta.product_service.service.BrandService;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Admin brand endpoints (ADMIN role required).
 *
 * <p>NOTE: method-level authorization ({@code @PreAuthorize("hasRole('ADMIN')")}) will be added
 * in the security-hardening phase once Spring Security is wired into product-service.
 */
@Tag(name = "Brands (admin)", description = "Admin brand CRUD")
@RestController
@RequestMapping("/api/v1/admin/brands")
@RequiredArgsConstructor
public class AdminBrandController {

    private final BrandService brandService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAllBrands() {

        List<BrandResponse> brands = brandService.getAllBrands();

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

    @PostMapping
    public ResponseEntity<BrandResponse> createBrand(
            @Valid @RequestBody BrandRequest request) {

        BrandResponse created = brandService.createBrand(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BrandResponse> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody BrandRequest request) {

        return ResponseEntity.ok(brandService.updateBrand(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBrand(@PathVariable Long id) {

        brandService.deleteBrand(id);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
