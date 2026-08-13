package com.ShopMeta.product_service.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ShopMeta.product_service.Entity.Product;
import com.ShopMeta.product_service.dto.ProductRequest;
import com.ShopMeta.product_service.dto.ProductResponse;
import com.ShopMeta.product_service.exception.ProductNotFoundException;
import com.ShopMeta.product_service.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    private ProductResponse mapToResponse(Product product) {

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .category(product.getCategory())
                .brand(product.getBrand())
                .imageUrl(product.getImageUrl())
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public ProductResponse createProduct(ProductRequest request) {

        if (productRepository.existsBySku(request.getSku())) {
            throw new RuntimeException("Product SKU already exists");
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .sku(request.getSku())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .category(request.getCategory())
                .brand(request.getBrand())
                .imageUrl(request.getImageUrl())
                .active(request.getActive() != null
                        ? request.getActive()
                        : true)
                .build();

        Product savedProduct = productRepository.save(product);

        return mapToResponse(savedProduct);
    }

    public List<ProductResponse> getAllProducts() {

        return productRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public Product getProductById(Long id) {

        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with id: " + id));
    }

    public Product updateProduct(Long id, Product updatedProduct) {

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with id: " + id));

        existingProduct.setName(updatedProduct.getName());
        existingProduct.setDescription(updatedProduct.getDescription());
        existingProduct.setPrice(updatedProduct.getPrice());
        existingProduct.setStockQuantity(updatedProduct.getStockQuantity());
        existingProduct.setCategory(updatedProduct.getCategory());
        existingProduct.setBrand(updatedProduct.getBrand());
        existingProduct.setImageUrl(updatedProduct.getImageUrl());
        existingProduct.setActive(updatedProduct.getActive());

        return productRepository.save(existingProduct);
    }

    public void deleteProduct(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with id: " + id));

        product.setActive(false);

        productRepository.save(product);
    }
}
