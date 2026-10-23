package com.ShopMeta.product_service.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ShopMeta.product_service.Entity.Brand;
import com.ShopMeta.product_service.Entity.Category;
import com.ShopMeta.product_service.Entity.Product;
import com.ShopMeta.product_service.Entity.ProductImage;
import com.ShopMeta.product_service.dto.ProductAttributeResponse;
import com.ShopMeta.product_service.dto.InternalProductResponse;
import com.ShopMeta.product_service.dto.ProductImageResponse;
import com.ShopMeta.product_service.dto.ProductPageResponse;
import com.ShopMeta.product_service.dto.ProductRequest;
import com.ShopMeta.product_service.dto.ProductResponse;
import com.ShopMeta.product_service.dto.ProductSummaryResponse;
import com.ShopMeta.product_service.exception.BusinessRuleException;
import com.ShopMeta.product_service.exception.DuplicateResourceException;
import com.ShopMeta.product_service.exception.InvalidRequestException;
import com.ShopMeta.product_service.exception.ResourceNotFoundException;
import com.ShopMeta.product_service.repository.BrandRepository;
import com.ShopMeta.product_service.repository.CategoryRepository;
import com.ShopMeta.product_service.repository.ProductRepository;
import com.ShopMeta.product_service.repository.ProductSpecification;
import com.ShopMeta.product_service.util.SlugUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String PRODUCT_IMAGE_DIRECTORY = "products";
    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("price", "name", "createdAt", "updatedAt", "averageRating", "ratingCount");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final FileStorageService fileStorageService;

    private Sort createSort(String sort) {

        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String[] sortParts = sort.split(",");

        String field = sortParts[0].trim();

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new InvalidRequestException(
                    "Invalid sort field '" + field + "'. Allowed fields: " + ALLOWED_SORT_FIELDS);
        }

        Sort.Direction direction = Sort.Direction.ASC;

        if (sortParts.length > 1) {
            try {
                direction = Sort.Direction.fromString(sortParts[1].trim());
            } catch (IllegalArgumentException exception) {
                throw new InvalidRequestException(
                        "Invalid sort direction '" + sortParts[1].trim() + "'. Use 'asc' or 'desc'.");
            }
        }

        return Sort.by(direction, field);
    }

    private int clampPageSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private void validatePrice(BigDecimal price, BigDecimal mrp) {
        if (mrp != null && price.compareTo(mrp) > 0) {
            throw new BusinessRuleException("Price cannot be greater than MRP");
        }
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    /**
     * Generates a URL slug from the name, appending a numeric suffix until it is unique.
     * {@code excludeProductId} allows a product to keep its own slug on update.
     */
    private String uniqueSlug(String name, Long excludeProductId) {

        String base = SlugUtils.toSlug(name);

        if (base == null || base.isBlank()) {
            base = "product";
        }

        String slug = base;
        int suffix = 2;

        while (true) {

            Optional<Product> existing = productRepository.findBySlug(slug);

            if (existing.isEmpty()
                    || (excludeProductId != null && existing.get().getId().equals(excludeProductId))) {
                return slug;
            }

            slug = base + "-" + suffix++;
        }
    }

    private String primaryImageUrl(Product product) {

        return product.getImages()
                .stream()
                .filter(image -> Boolean.TRUE.equals(image.getPrimary()))
                .findFirst()
                .or(() -> product.getImages().stream().findFirst())
                .map(ProductImage::getUrl)
                .orElse(null);
    }

    private ProductImageResponse mapImage(ProductImage image) {

        return ProductImageResponse.builder()
                .id(image.getId())
                .url(image.getUrl())
                .altText(image.getAltText())
                .sortOrder(image.getSortOrder())
                .primary(image.getPrimary())
                .build();
    }

    private ProductSummaryResponse mapToSummary(Product product) {

        Category category = product.getCategory();
        Brand brand = product.getBrand();

        return ProductSummaryResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDescription(product.getShortDescription())
                .price(product.getPrice())
                .mrp(product.getMrp())
                .currency(product.getCurrency())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .brandId(brand != null ? brand.getId() : null)
                .brandName(brand != null ? brand.getName() : null)
                .imageUrl(primaryImageUrl(product))
                .active(product.getActive())
                .averageRating(product.getAverageRating())
                .ratingCount(product.getRatingCount())
                .createdAt(product.getCreatedAt())
                .build();
    }

    private ProductResponse mapToResponse(Product product) {

        Category category = product.getCategory();
        Brand brand = product.getBrand();

        List<ProductImageResponse> images = product.getImages()
                .stream()
                .map(this::mapImage)
                .toList();

        List<ProductAttributeResponse> attributes = product.getAttributes()
                .stream()
                .map(attribute -> ProductAttributeResponse.builder()
                        .key(attribute.getAttrKey())
                        .value(attribute.getAttrValue())
                        .build())
                .toList();

        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .price(product.getPrice())
                .mrp(product.getMrp())
                .currency(product.getCurrency())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .brandId(brand != null ? brand.getId() : null)
                .brandName(brand != null ? brand.getName() : null)
                .active(product.getActive())
                .averageRating(product.getAverageRating())
                .ratingCount(product.getRatingCount())
                .images(images)
                .attributes(attributes)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    // ---------------------------------------------------------------------
    // Write operations (admin)
    // ---------------------------------------------------------------------

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {

        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateResourceException("Product SKU already exists: " + request.getSku());
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.getCategoryId()));

        Brand brand = brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Brand not found with id: " + request.getBrandId()));

        validatePrice(request.getPrice(), request.getMrp());

        Product product = Product.builder()
                .sku(request.getSku())
                .name(request.getName())
                .slug(uniqueSlug(request.getName(), null))
                .shortDescription(request.getShortDescription())
                .description(request.getDescription())
                .price(request.getPrice())
                .mrp(request.getMrp())
                .category(category)
                .brand(brand)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Product savedProduct = productRepository.save(product);

        return mapToResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {

        Product existingProduct = findProduct(id);

        if (!existingProduct.getSku().equals(request.getSku())
                && productRepository.existsBySku(request.getSku())) {
            throw new DuplicateResourceException("Product SKU already exists: " + request.getSku());
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.getCategoryId()));

        Brand brand = brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Brand not found with id: " + request.getBrandId()));

        validatePrice(request.getPrice(), request.getMrp());

        existingProduct.setSku(request.getSku());
        existingProduct.setName(request.getName());
        existingProduct.setSlug(uniqueSlug(request.getName(), existingProduct.getId()));
        existingProduct.setShortDescription(request.getShortDescription());
        existingProduct.setDescription(request.getDescription());
        existingProduct.setPrice(request.getPrice());
        existingProduct.setMrp(request.getMrp());
        existingProduct.setCategory(category);
        existingProduct.setBrand(brand);

        if (request.getActive() != null) {
            existingProduct.setActive(request.getActive());
        }

        Product savedProduct = productRepository.save(existingProduct);

        return mapToResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updateProductStatus(Long id, boolean active) {

        Product product = findProduct(id);

        product.setActive(active);

        return mapToResponse(productRepository.save(product));
    }

    /**
     * Soft delete: the product is deactivated but kept so historical orders still resolve.
     */
    @Transactional
    public void deleteProduct(Long id) {

        Product product = findProduct(id);

        product.setActive(false);

        productRepository.save(product);
    }

    @Transactional
    public ProductImageResponse addImage(Long productId, MultipartFile file, String altText, boolean primary) {

        Product product = findProduct(productId);

        String url = fileStorageService.storeImage(file, PRODUCT_IMAGE_DIRECTORY);

        boolean makePrimary = primary || product.getImages().isEmpty();

        if (makePrimary) {
            product.getImages().forEach(image -> image.setPrimary(false));
        }

        ProductImage image = ProductImage.builder()
                .product(product)
                .url(url)
                .altText(altText)
                .sortOrder(product.getImages().size())
                .primary(makePrimary)
                .build();

        product.getImages().add(image);

        productRepository.save(product);

        return mapImage(image);
    }

    @Transactional
    public void deleteImage(Long productId, Long imageId) {

        Product product = findProduct(productId);

        ProductImage image = product.getImages()
                .stream()
                .filter(candidate -> candidate.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image not found with id: " + imageId + " for product " + productId));

        product.getImages().remove(image);

        productRepository.save(product);

        fileStorageService.deleteByUrl(image.getUrl());
    }

    // ---------------------------------------------------------------------
    // Read operations
    // ---------------------------------------------------------------------

    /**
     * Public catalog listing (spec section 8.3 GET /api/products). Only active products.
     */
    @Transactional(readOnly = true)
    public ProductPageResponse getAllProducts(
            int page,
            int size,
            String sort,
            String q,
            String categorySlug,
            String brandSlug,
            BigDecimal minPrice,
            BigDecimal maxPrice) {

        return getProducts(page, size, sort, q, categorySlug, brandSlug, minPrice, maxPrice, Boolean.TRUE);
    }

    /**
     * Admin listing: includes inactive products; {@code active} may be null to return any status.
     */
    @Transactional(readOnly = true)
    public ProductPageResponse getAllProductsForAdmin(
            int page,
            int size,
            String sort,
            String q,
            String categorySlug,
            String brandSlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean active) {

        return getProducts(page, size, sort, q, categorySlug, brandSlug, minPrice, maxPrice, active);
    }

    private ProductPageResponse getProducts(
            int page,
            int size,
            String sort,
            String q,
            String categorySlug,
            String brandSlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean active) {

        Sort sorting = createSort(sort);

        Pageable pageable = PageRequest.of(Math.max(page, 0), clampPageSize(size), sorting);

        Specification<Product> specification = ProductSpecification.filterProducts(
                q, categorySlug, brandSlug, minPrice, maxPrice, active);

        Page<Product> productPage = productRepository.findAll(specification, pageable);

        List<ProductSummaryResponse> products = productPage.getContent()
                .stream()
                .map(this::mapToSummary)
                .toList();

        return ProductPageResponse.builder()
                .content(products)
                .page(productPage.getNumber())
                .size(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .last(productPage.isLast())
                .build();
    }

    /**
     * Public product detail by id (spec section 8.3). Inactive products are not exposed.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        Product product = findProduct(id);

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }

        return mapToResponse(product);
    }

    /**
     * Admin product detail by id. Returns the product regardless of status.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductByIdForAdmin(Long id) {

        return mapToResponse(findProduct(id));
    }

    /**
     * Public product detail by slug (spec section 8.3). Inactive products are not exposed.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug) {

        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with slug: " + slug));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new ResourceNotFoundException("Product not found with slug: " + slug);
        }

        return mapToResponse(product);
    }

    // ---------------------------------------------------------------------
    // Internal (service-to-service) operations
    // ---------------------------------------------------------------------

    private InternalProductResponse mapToInternal(Product product) {

        return InternalProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .price(product.getPrice())
                .imageUrl(primaryImageUrl(product))
                .active(product.getActive())
                .build();
    }

    /**
     * Bulk product snapshot for cart-service / order-service (spec section 8.3).
     * Null ids are ignored and duplicates are collapsed. Returns inactive products too.
     */
    @Transactional(readOnly = true)
    public List<InternalProductResponse> getProductsByIds(List<Long> ids) {

        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Long> distinctIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (distinctIds.isEmpty()) {
            return List.of();
        }

        return productRepository.findAllById(distinctIds)
                .stream()
                .map(this::mapToInternal)
                .toList();
    }

    /**
     * Single product snapshot for internal callers. Returns the product regardless of status.
     */
    @Transactional(readOnly = true)
    public InternalProductResponse getInternalProductById(Long id) {

        return mapToInternal(findProduct(id));
    }
}
