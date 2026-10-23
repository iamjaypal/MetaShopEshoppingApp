package com.ShopMeta.product_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.ShopMeta.product_service.Entity.Brand;
import com.ShopMeta.product_service.Entity.Category;
import com.ShopMeta.product_service.Entity.Product;
import com.ShopMeta.product_service.Entity.ProductImage;
import com.ShopMeta.product_service.dto.InternalProductResponse;
import com.ShopMeta.product_service.dto.ProductImageResponse;
import com.ShopMeta.product_service.dto.ProductRequest;
import com.ShopMeta.product_service.dto.ProductResponse;
import com.ShopMeta.product_service.exception.BusinessRuleException;
import com.ShopMeta.product_service.exception.DuplicateResourceException;
import com.ShopMeta.product_service.exception.InvalidRequestException;
import com.ShopMeta.product_service.exception.ResourceNotFoundException;
import com.ShopMeta.product_service.repository.BrandRepository;
import com.ShopMeta.product_service.repository.CategoryRepository;
import com.ShopMeta.product_service.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ProductService productService;

    private ProductRequest request() {
        ProductRequest request = new ProductRequest();
        request.setName("iPhone 15");
        request.setSku("SKU-1");
        request.setPrice(new BigDecimal("79999.00"));
        request.setMrp(new BigDecimal("89999.00"));
        request.setCategoryId(1L);
        request.setBrandId(2L);
        return request;
    }

    private void stubCategoryAndBrand() {
        given(categoryRepository.findById(1L))
                .willReturn(Optional.of(Category.builder().id(1L).name("Phones").slug("phones").build()));
        given(brandRepository.findById(2L))
                .willReturn(Optional.of(Brand.builder().id(2L).name("Apple").slug("apple").build()));
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[] { 1, 2, 3 });
    }

    // ---------------------------------------------------------------- create

    @Test
    void createProduct_setsSlugBrandAndCategory() {

        given(productRepository.existsBySku("SKU-1")).willReturn(false);
        stubCategoryAndBrand();
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(10L);
            return product;
        });

        ProductResponse response = productService.createProduct(request());

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getSlug()).isEqualTo("iphone-15");
        assertThat(response.getBrandName()).isEqualTo("Apple");
        assertThat(response.getCategoryName()).isEqualTo("Phones");
    }

    @Test
    void createProduct_duplicateName_appendsUniqueSlugSuffix() {

        given(productRepository.existsBySku("SKU-1")).willReturn(false);
        stubCategoryAndBrand();
        given(productRepository.findBySlug("iphone-15"))
                .willReturn(Optional.of(Product.builder().id(99L).build()));
        given(productRepository.findBySlug("iphone-15-2")).willReturn(Optional.empty());
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(11L);
            return product;
        });

        ProductResponse response = productService.createProduct(request());

        assertThat(response.getSlug()).isEqualTo("iphone-15-2");
    }

    @Test
    void createProduct_duplicateSku_throwsDuplicateResourceException() {

        given(productRepository.existsBySku("SKU-1")).willReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request()))
                .isInstanceOf(DuplicateResourceException.class);

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_priceAboveMrp_throwsBusinessRuleException() {

        ProductRequest request = request();
        request.setPrice(new BigDecimal("99999.00"));

        given(productRepository.existsBySku("SKU-1")).willReturn(false);
        stubCategoryAndBrand();

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(BusinessRuleException.class);

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_categoryNotFound_throwsResourceNotFoundException() {

        given(productRepository.existsBySku("SKU-1")).willReturn(false);
        given(categoryRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------- status

    @Test
    void updateProductStatus_changesActiveFlag() {

        Product product = Product.builder().id(1L).name("iPhone 15").slug("iphone-15").active(true).build();

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.updateProductStatus(1L, false);

        assertThat(response.getActive()).isFalse();
    }

    // ------------------------------------------------------------- images

    @Test
    void addImage_firstImageBecomesPrimaryAndIsStored() {

        Product product = Product.builder().id(1L).name("iPhone 15").slug("iphone-15").active(true).build();

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(fileStorageService.storeImage(any(MultipartFile.class), eq("products")))
                .willReturn("/uploads/products/abc.png");
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

        ProductImageResponse image = productService.addImage(1L, imageFile(), "front", false);

        assertThat(image.getUrl()).isEqualTo("/uploads/products/abc.png");
        assertThat(image.getPrimary()).isTrue();
        assertThat(product.getImages()).hasSize(1);
    }

    @Test
    void addImage_primaryFlagUnsetsPreviousPrimary() {

        Product product = Product.builder().id(1L).name("iPhone 15").slug("iphone-15").active(true).build();
        ProductImage existing = ProductImage.builder().id(1L).product(product)
                .url("/uploads/products/old.png").primary(true).build();
        product.getImages().add(existing);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(fileStorageService.storeImage(any(MultipartFile.class), eq("products")))
                .willReturn("/uploads/products/new.png");
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

        productService.addImage(1L, imageFile(), "back", true);

        assertThat(existing.getPrimary()).isFalse();
    }

    @Test
    void deleteImage_removesImageAndDeletesFile() {

        Product product = Product.builder().id(1L).name("iPhone 15").slug("iphone-15").active(true).build();
        ProductImage image = ProductImage.builder().id(5L).product(product)
                .url("/uploads/products/abc.png").primary(true).build();
        product.getImages().add(image);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

        productService.deleteImage(1L, 5L);

        assertThat(product.getImages()).isEmpty();
        verify(fileStorageService).deleteByUrl("/uploads/products/abc.png");
    }

    @Test
    void deleteImage_notFound_throwsResourceNotFoundException() {

        Product product = Product.builder().id(1L).name("iPhone 15").slug("iphone-15").active(true).build();

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.deleteImage(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --------------------------------------------------------------- reads

    @Test
    void getAllProducts_clampsPageSizeTo100() {

        given(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        productService.getAllProducts(0, 500, "price,asc", null, null, null, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(any(Specification.class), captor.capture());

        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort().getOrderFor("price").getDirection().isAscending()).isTrue();
    }

    @Test
    void getAllProducts_invalidSortField_throwsInvalidRequestException() {

        assertThatThrownBy(() -> productService.getAllProducts(0, 20, "hacker,asc", null, null, null, null, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getAllProducts_invalidSortDirection_throwsInvalidRequestException() {

        assertThatThrownBy(() -> productService.getAllProducts(0, 20, "price,sideways", null, null, null, null, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getAllProductsForAdmin_allowsInactiveFilter() {

        given(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        productService.getAllProductsForAdmin(0, 20, "createdAt,desc", null, null, null, null, null, false);

        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getProductById_notFound_throwsResourceNotFoundException() {

        given(productRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProductById_inactive_throwsResourceNotFoundException() {

        Product inactive = Product.builder().id(1L).name("Old").slug("old").active(false).build();

        given(productRepository.findById(1L)).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> productService.getProductById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProductByIdForAdmin_returnsInactiveProduct() {

        Product inactive = Product.builder().id(1L).name("Old").slug("old").active(false).build();

        given(productRepository.findById(1L)).willReturn(Optional.of(inactive));

        ProductResponse response = productService.getProductByIdForAdmin(1L);

        assertThat(response.getActive()).isFalse();
    }

    @Test
    void getProductBySlug_notFound_throwsResourceNotFoundException() {

        given(productRepository.findBySlug("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductBySlug("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------ internal

    @Test
    void getProductsByIds_dedupesAndMaps() {

        Product product = Product.builder().id(1L).sku("SKU-1").name("iPhone")
                .price(new BigDecimal("79999.00")).active(true).build();

        given(productRepository.findAllById(anyList())).willReturn(List.of(product));

        List<InternalProductResponse> result = productService.getProductsByIds(List.of(1L, 1L, 1L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSku()).isEqualTo("SKU-1");
        assertThat(result.get(0).getActive()).isTrue();
    }

    @Test
    void getProductsByIds_emptyInput_returnsEmptyWithoutQuerying() {

        assertThat(productService.getProductsByIds(List.of())).isEmpty();

        verify(productRepository, never()).findAllById(anyList());
    }

    @Test
    void getInternalProductById_returnsInactiveProduct() {

        Product inactive = Product.builder().id(1L).sku("SKU-1").name("Old")
                .price(new BigDecimal("10.00")).active(false).build();

        given(productRepository.findById(1L)).willReturn(Optional.of(inactive));

        InternalProductResponse response = productService.getInternalProductById(1L);

        assertThat(response.getActive()).isFalse();
    }
}
