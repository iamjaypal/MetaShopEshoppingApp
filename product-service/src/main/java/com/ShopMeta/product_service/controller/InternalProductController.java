package com.ShopMeta.product_service.controller;

import com.ShopMeta.product_service.dto.InternalProductResponse;
import com.ShopMeta.product_service.dto.ProductBatchRequest;
import com.ShopMeta.product_service.service.ProductService;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal, service-to-service product endpoints (spec section 8.3).
 *
 * <p>These must NOT be exposed publicly by the API Gateway and will additionally be protected
 * by the shared {@code X-Internal-Token} header once security is wired in (spec section 5.4).
 */
@Tag(name = "Products (internal)", description = "Service-to-service product lookups (not publicly routed)")
@RestController
@RequestMapping("/api/v1/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService productService;

    @Operation(summary = "Batch product lookup", description = "Returns id, sku, name, price, imageUrl, active for the given ids.")
    @PostMapping("/batch")
    public ResponseEntity<List<InternalProductResponse>> getProductsBatch(
            @Valid @RequestBody ProductBatchRequest request) {

        return ResponseEntity.ok(productService.getProductsByIds(request.getIds()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InternalProductResponse> getProductById(@PathVariable Long id) {

        return ResponseEntity.ok(productService.getInternalProductById(id));
    }
}
