package com.ShopMeta.product_service.service;

import com.ShopMeta.product_service.Entity.Brand;
import com.ShopMeta.product_service.dto.BrandRequest;
import com.ShopMeta.product_service.dto.BrandResponse;
import com.ShopMeta.product_service.exception.DuplicateResourceException;
import com.ShopMeta.product_service.exception.ResourceNotFoundException;
import com.ShopMeta.product_service.repository.BrandRepository;
import com.ShopMeta.product_service.util.SlugUtils;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    private BrandResponse mapToResponse(Brand brand) {

        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .slug(brand.getSlug())
                .logoUrl(brand.getLogoUrl())
                .active(brand.getActive())
                .createdAt(brand.getCreatedAt())
                .updatedAt(brand.getUpdatedAt())
                .build();
    }

    private String resolveSlug(String providedSlug, String name) {
        if (providedSlug != null && !providedSlug.isBlank()) {
            return SlugUtils.toSlug(providedSlug);
        }
        return SlugUtils.toSlug(name);
    }

    /**
     * Public brand list. Returns only active brands, ordered by name.
     */
    @Transactional(readOnly = true)
    public List<BrandResponse> getActiveBrands() {

        return brandRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Admin brand list. Returns all brands (including inactive), ordered by name.
     */
    @Transactional(readOnly = true)
    public List<BrandResponse> getAllBrands() {

        return brandRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BrandResponse getBrandById(Long id) {

        return mapToResponse(findBrand(id));
    }

    @Transactional
    public BrandResponse createBrand(BrandRequest request) {

        if (brandRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Brand already exists with name: " + request.getName());
        }

        String slug = resolveSlug(request.getSlug(), request.getName());

        if (brandRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Brand already exists with slug: " + slug);
        }

        Brand brand = Brand.builder()
                .name(request.getName())
                .slug(slug)
                .logoUrl(request.getLogoUrl())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        return mapToResponse(brandRepository.save(brand));
    }

    @Transactional
    public BrandResponse updateBrand(Long id, BrandRequest request) {

        Brand brand = findBrand(id);

        if (!brand.getName().equalsIgnoreCase(request.getName())
                && brandRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Brand already exists with name: " + request.getName());
        }

        String slug = resolveSlug(request.getSlug(), request.getName());

        if (!slug.equals(brand.getSlug()) && brandRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Brand already exists with slug: " + slug);
        }

        brand.setName(request.getName());
        brand.setSlug(slug);
        brand.setLogoUrl(request.getLogoUrl());

        if (request.getActive() != null) {
            brand.setActive(request.getActive());
        }

        return mapToResponse(brandRepository.save(brand));
    }

    /**
     * Soft delete: the brand is deactivated but kept so historical products/orders still resolve.
     */
    @Transactional
    public void deleteBrand(Long id) {

        Brand brand = findBrand(id);

        brand.setActive(false);

        brandRepository.save(brand);
    }

    private Brand findBrand(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));
    }
}
