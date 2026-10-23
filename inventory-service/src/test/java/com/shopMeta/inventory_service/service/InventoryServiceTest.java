package com.shopMeta.inventory_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.shopMeta.inventory_service.config.InventoryProperties;
import com.shopMeta.inventory_service.dto.AvailabilityResponse;
import com.shopMeta.inventory_service.dto.InventoryPageResponse;
import com.shopMeta.inventory_service.dto.InventoryResponse;
import com.shopMeta.inventory_service.dto.StockCreateRequest;
import com.shopMeta.inventory_service.dto.StockUpdateRequest;
import com.shopMeta.inventory_service.entity.Inventory;
import com.shopMeta.inventory_service.entity.StockMovementReason;
import com.shopMeta.inventory_service.exception.BusinessRuleException;
import com.shopMeta.inventory_service.exception.InvalidRequestException;
import com.shopMeta.inventory_service.exception.ResourceNotFoundException;
import com.shopMeta.inventory_service.repository.InventoryRepository;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockMovementRecorder stockMovementRecorder;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        InventoryProperties properties = new InventoryProperties();
        properties.setDefaultLowStockThreshold(5);
        properties.setDisplayQuantityCap(10);

        inventoryService = new InventoryService(inventoryRepository, stockMovementRecorder, properties);
    }

    private Inventory stock(long productId, int available, int reserved, int threshold) {
        return Inventory.builder()
                .id(productId)
                .productId(productId)
                .sku("SKU-" + productId)
                .availableQuantity(available)
                .reservedQuantity(reserved)
                .lowStockThreshold(threshold)
                .build();
    }

    private void givenStock(Inventory stock) {
        given(inventoryRepository.findByProductId(stock.getProductId()))
                .willReturn(Optional.of(stock));
    }

    private void givenSaveEchoes() {
        given(inventoryRepository.save(any(Inventory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------------ public availability

    @Test
    @DisplayName("availability: caps the displayed quantity so exact stock is not leaked")
    void availability_capsDisplayedQuantity() {

        givenStock(stock(1, 312, 0, 5));

        AvailabilityResponse response = inventoryService.getAvailability(1L);

        assertThat(response.getProductId()).isEqualTo(1L);
        assertThat(response.getAvailableQuantity()).isEqualTo(10);
        assertThat(response.isInStock()).isTrue();
    }

    @Test
    @DisplayName("availability: reports the real number when it is below the cap")
    void availability_reportsSmallQuantityExactly() {

        givenStock(stock(1, 3, 0, 5));

        assertThat(inventoryService.getAvailability(1L).getAvailableQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("availability: zero stock is reported as out of stock")
    void availability_zeroIsOutOfStock() {

        givenStock(stock(1, 0, 0, 5));

        AvailabilityResponse response = inventoryService.getAvailability(1L);

        assertThat(response.isInStock()).isFalse();
        assertThat(response.getAvailableQuantity()).isZero();
    }

    @Test
    @DisplayName("availability: an unknown product is a 404")
    void availability_unknownProductIsNotFound() {

        given(inventoryRepository.findByProductId(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getAvailability(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ------------------------------------------------------------------ admin create

    @Test
    @DisplayName("create: applies the default threshold and logs an INITIAL movement")
    void create_appliesDefaultThresholdAndLogsInitialMovement() {

        given(inventoryRepository.existsByProductId(1L)).willReturn(false);
        givenSaveEchoes();

        InventoryResponse response = inventoryService.createStock(
                StockCreateRequest.builder().productId(1L).sku("SKU-1").quantity(25).build(),
                "admin@metashop.com");

        assertThat(response.getProductId()).isEqualTo(1L);
        assertThat(response.getAvailableQuantity()).isEqualTo(25);
        assertThat(response.getReservedQuantity()).isZero();
        assertThat(response.getLowStockThreshold()).isEqualTo(5);
        assertThat(response.isInStock()).isTrue();

        then(stockMovementRecorder).should()
                .record(1L, 25, StockMovementReason.INITIAL, "admin@metashop.com");
    }

    @Test
    @DisplayName("create: honours an explicit threshold")
    void create_honoursExplicitThreshold() {

        given(inventoryRepository.existsByProductId(1L)).willReturn(false);
        givenSaveEchoes();

        InventoryResponse response = inventoryService.createStock(
                StockCreateRequest.builder().productId(1L).quantity(2).lowStockThreshold(3).build(),
                "admin@metashop.com");

        assertThat(response.getLowStockThreshold()).isEqualTo(3);
        assertThat(response.isLowStock()).isTrue();
    }

    @Test
    @DisplayName("create: a second stock row for the same product is a 422")
    void create_rejectsDuplicateProduct() {

        given(inventoryRepository.existsByProductId(1L)).willReturn(true);

        assertThatThrownBy(() -> inventoryService.createStock(
                StockCreateRequest.builder().productId(1L).quantity(1).build(), "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");

        then(inventoryRepository).should(never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("create: zero opening stock logs no movement")
    void create_zeroQuantityLogsNoMovement() {

        given(inventoryRepository.existsByProductId(1L)).willReturn(false);
        givenSaveEchoes();

        inventoryService.createStock(
                StockCreateRequest.builder().productId(1L).quantity(0).build(), "admin");

        then(stockMovementRecorder).should(never())
                .record(any(), anyInt(), any(), any());
    }

    // ------------------------------------------------------------------ admin update

    @Test
    @DisplayName("update: 'quantity' sets the absolute value and logs the delta")
    void update_setsAbsoluteQuantity() {

        givenStock(stock(1, 10, 0, 5));
        givenSaveEchoes();

        InventoryResponse response = inventoryService.updateStock(
                1L, StockUpdateRequest.builder().quantity(40).build(), "admin@metashop.com");

        assertThat(response.getAvailableQuantity()).isEqualTo(40);
        then(stockMovementRecorder).should()
                .record(1L, 30, StockMovementReason.ADMIN_ADJUSTMENT, "admin@metashop.com");
    }

    @Test
    @DisplayName("update: 'adjustment' applies a signed delta")
    void update_appliesSignedDelta() {

        givenStock(stock(1, 10, 0, 5));
        givenSaveEchoes();

        InventoryResponse response = inventoryService.updateStock(
                1L, StockUpdateRequest.builder().adjustment(-4).build(), "admin@metashop.com");

        assertThat(response.getAvailableQuantity()).isEqualTo(6);
        then(stockMovementRecorder).should()
                .record(1L, -4, StockMovementReason.ADMIN_ADJUSTMENT, "admin@metashop.com");
    }

    @Test
    @DisplayName("update: supplying both quantity and adjustment is a 400")
    void update_rejectsBothQuantityAndAdjustment() {

        assertThatThrownBy(() -> inventoryService.updateStock(
                1L, StockUpdateRequest.builder().quantity(5).adjustment(2).build(), "admin"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    @DisplayName("update: supplying neither quantity nor adjustment is a 400")
    void update_rejectsNeitherQuantityNorAdjustment() {

        assertThatThrownBy(() -> inventoryService.updateStock(
                1L, StockUpdateRequest.builder().build(), "admin"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    @DisplayName("update: an adjustment that would go below zero is a 422")
    void update_rejectsNegativeResult() {

        givenStock(stock(1, 3, 0, 5));

        assertThatThrownBy(() -> inventoryService.updateStock(
                1L, StockUpdateRequest.builder().adjustment(-10).build(), "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("below zero");

        then(inventoryRepository).should(never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("update: an unknown product is a 404")
    void update_unknownProductIsNotFound() {

        given(inventoryRepository.findByProductId(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.updateStock(
                99L, StockUpdateRequest.builder().quantity(1).build(), "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update: a zero-delta set logs no movement")
    void update_zeroDeltaLogsNoMovement() {

        givenStock(stock(1, 10, 0, 5));
        givenSaveEchoes();

        inventoryService.updateStock(
                1L, StockUpdateRequest.builder().quantity(10).build(), "admin");

        then(stockMovementRecorder).should(never()).record(any(), anyInt(), any(), any());
    }

    // ------------------------------------------------------------------ low stock

    @Test
    @DisplayName("low-stock: returns the paginated envelope and clamps the page size")
    void lowStock_returnsPagedEnvelope() {

        Page<Inventory> page = new PageImpl<>(
                List.of(stock(1, 2, 0, 5), stock(2, 0, 0, 5)),
                PageRequest.of(0, 100),
                2);
        given(inventoryRepository.findLowStock(any(Pageable.class))).willReturn(page);

        InventoryPageResponse response = inventoryService.findLowStock(null, 0, 500);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).isLowStock()).isTrue();
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(100);
        assertThat(response.isLast()).isTrue();
    }

    @Test
    @DisplayName("low-stock: an explicit threshold overrides the per-row one")
    void lowStock_honoursExplicitThreshold() {

        given(inventoryRepository.findLowStockBelow(eq(3), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        inventoryService.findLowStock(3, 0, 20);

        then(inventoryRepository).should().findLowStockBelow(eq(3), any(Pageable.class));
        then(inventoryRepository).should(never()).findLowStock(any(Pageable.class));
    }
}
