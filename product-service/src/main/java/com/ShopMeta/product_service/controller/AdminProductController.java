package com.ShopMeta.product_service.controller;

import com.ShopMeta.product_service.dto.ApiResponse;
import com.ShopMeta.product_service.dto.ProductImageResponse;
import com.ShopMeta.product_service.dto.ProductPageResponse;
import com.ShopMeta.product_service.dto.ProductRequest;
import com.ShopMeta.product_service.dto.ProductResponse;
import com.ShopMeta.product_service.dto.ProductStatusRequest;
import com.ShopMeta.product_service.service.ProductService;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;

/**
 * Admin product endpoints (ADMIN role required).
 *
 * <p>NOTE: method-level authorization will be added in the security-hardening phase.
 * ProductCreatedEvent / ProductPriceChangedEvent publishing is deferred to the Kafka phase.
 */
@Tag(name = "Products (admin)", description = "Admin product management: CRUD, status and images")
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProductPageResponse>> getAllProducts(

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "20") int size,

            @RequestParam(defaultValue = "createdAt,desc") String sort,

            @RequestParam(required = false) String q,

            @RequestParam(required = false) String category,

            @RequestParam(required = false) String brand,

            @RequestParam(required = false) BigDecimal minPrice,

            @RequestParam(required = false) BigDecimal maxPrice,

            @RequestParam(required = false) Boolean active) {

        ProductPageResponse response = productService.getAllProductsForAdmin(
                page, size, sort, q, category, brand, minPrice, maxPrice, active);

        return ResponseEntity.ok(
                ApiResponse.<ProductPageResponse>builder()
                        .success(true)
                        .message("Products fetched successfully")
                        .data(response)
                        .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {

        return ResponseEntity.ok(productService.getProductByIdForAdmin(id));
    }

    @Operation(summary = "Create a product")
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request) {

        ProductResponse created = productService.createProduct(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {

        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductResponse> updateProductStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProductStatusRequest request) {

        return ResponseEntity.ok(productService.updateProductStatus(id, request.getActive()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {

        productService.deleteProduct(id);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Operation(summary = "Upload a product image (multipart)")
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> uploadImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String altText,
            @RequestParam(defaultValue = "false") boolean primary) {

        ProductImageResponse image = productService.addImage(id, file, altText, primary);

        return ResponseEntity.status(HttpStatus.CREATED).body(image);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long id,
            @PathVariable Long imageId) {

        productService.deleteImage(id, imageId);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
