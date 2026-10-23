package com.ShopMeta.product_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ShopMeta.product_service.Entity.Brand;
import com.ShopMeta.product_service.dto.BrandRequest;
import com.ShopMeta.product_service.dto.BrandResponse;
import com.ShopMeta.product_service.exception.DuplicateResourceException;
import com.ShopMeta.product_service.exception.ResourceNotFoundException;
import com.ShopMeta.product_service.repository.BrandRepository;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    private BrandRequest request(String name) {
        BrandRequest request = new BrandRequest();
        request.setName(name);
        return request;
    }

    @Test
    void createBrand_generatesSlugAndDefaultsActiveTrue() {

        given(brandRepository.existsByNameIgnoreCase("Samsung")).willReturn(false);
        given(brandRepository.existsBySlug("samsung")).willReturn(false);
        given(brandRepository.save(any(Brand.class))).willAnswer(invocation -> {
            Brand brand = invocation.getArgument(0);
            brand.setId(1L);
            return brand;
        });

        BrandResponse response = brandService.createBrand(request("Samsung"));

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Samsung");
        assertThat(response.getSlug()).isEqualTo("samsung");
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void createBrand_duplicateName_throwsDuplicateResourceException() {

        given(brandRepository.existsByNameIgnoreCase("Samsung")).willReturn(true);

        assertThatThrownBy(() -> brandService.createBrand(request("Samsung")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Samsung");

        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    void createBrand_duplicateSlug_throwsDuplicateResourceException() {

        given(brandRepository.existsByNameIgnoreCase("Samsung")).willReturn(false);
        given(brandRepository.existsBySlug("samsung")).willReturn(true);

        assertThatThrownBy(() -> brandService.createBrand(request("Samsung")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    void getBrandById_notFound_throwsResourceNotFoundException() {

        given(brandRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> brandService.getBrandById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateBrand_changesNameAndSlug() {

        Brand existing = Brand.builder().id(1L).name("Samsung").slug("samsung").active(true).build();

        given(brandRepository.findById(1L)).willReturn(Optional.of(existing));
        given(brandRepository.existsByNameIgnoreCase("Samsung Electronics")).willReturn(false);
        given(brandRepository.existsBySlug("samsung-electronics")).willReturn(false);
        given(brandRepository.save(any(Brand.class))).willAnswer(invocation -> invocation.getArgument(0));

        BrandResponse response = brandService.updateBrand(1L, request("Samsung Electronics"));

        assertThat(response.getName()).isEqualTo("Samsung Electronics");
        assertThat(response.getSlug()).isEqualTo("samsung-electronics");
    }

    @Test
    void deleteBrand_softDeletes() {

        Brand existing = Brand.builder().id(1L).name("Samsung").slug("samsung").active(true).build();

        given(brandRepository.findById(1L)).willReturn(Optional.of(existing));
        given(brandRepository.save(any(Brand.class))).willAnswer(invocation -> invocation.getArgument(0));

        brandService.deleteBrand(1L);

        assertThat(existing.getActive()).isFalse();
        verify(brandRepository).save(existing);
    }

    @Test
    void getActiveBrands_returnsMappedList() {

        given(brandRepository.findByActiveTrueOrderByNameAsc())
                .willReturn(List.of(Brand.builder().id(1L).name("Apple").slug("apple").active(true).build()));

        List<BrandResponse> brands = brandService.getActiveBrands();

        assertThat(brands).hasSize(1);
        assertThat(brands.get(0).getSlug()).isEqualTo("apple");
    }
}
