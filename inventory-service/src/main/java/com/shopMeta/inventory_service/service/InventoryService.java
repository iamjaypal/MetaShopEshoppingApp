package com.shopMeta.inventory_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;

/**
 * Public availability lookup and admin stock management (spec 9.3).
 *
 * <p>Reservation handling lives in {@link ReservationService}; this service only ever changes the
 * counters by hand, for an admin.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final InventoryRepository inventoryRepository;
    private final StockMovementRecorder stockMovementRecorder;
    private final InventoryProperties properties;

    /**
     * Public availability answer (spec 9.3). The exact quantity is capped so the storefront cannot be
     * scraped for stock levels.
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(Long productId) {

        Inventory stock = findStock(productId);

        return AvailabilityResponse.builder()
                .productId(stock.getProductId())
                .inStock(stock.isInStock())
                .availableQuantity(Math.min(stock.getAvailableQuantity(),
                        properties.getDisplayQuantityCap()))
                .build();
    }

    /** Full stock view for admins. */
    @Transactional(readOnly = true)
    public InventoryResponse getStock(Long productId) {

        return toResponse(findStock(productId));
    }

    /** Creates the stock row for a product (spec 9.3, {@code POST /api/admin/inventory}). */
    @Transactional
    public InventoryResponse createStock(StockCreateRequest request, String actor) {

        if (inventoryRepository.existsByProductId(request.getProductId())) {
            throw new BusinessRuleException(
                    "Inventory already exists for product " + request.getProductId());
        }

        Inventory stock = Inventory.builder()
                .productId(request.getProductId())
                .sku(request.getSku())
                .availableQuantity(request.getQuantity())
                .reservedQuantity(0)
                .lowStockThreshold(request.getLowStockThreshold() != null
                        ? request.getLowStockThreshold()
                        : properties.getDefaultLowStockThreshold())
                .build();

        Inventory saved = inventoryRepository.save(stock);

        if (request.getQuantity() > 0) {
            stockMovementRecorder.record(saved.getProductId(), request.getQuantity(),
                    StockMovementReason.INITIAL, actor);
        }

        return toResponse(saved);
    }

    /**
     * Sets or adjusts the available quantity and optionally the threshold (spec 9.3,
     * {@code PUT /api/admin/inventory/{productId}}). Every change is logged as a movement.
     *
     * <p>Exactly one of {@code quantity} (absolute) or {@code adjustment} (signed delta) must be
     * supplied.
     */
    @Transactional
    public InventoryResponse updateStock(Long productId, StockUpdateRequest request, String actor) {

        boolean hasQuantity = request.getQuantity() != null;
        boolean hasAdjustment = request.getAdjustment() != null;

        if (hasQuantity == hasAdjustment) {
            throw new InvalidRequestException(
                    "Provide exactly one of 'quantity' (set the absolute value) "
                            + "or 'adjustment' (a signed delta)");
        }

        Inventory stock = findStock(productId);

        int current = stock.getAvailableQuantity();
        int target = hasQuantity ? request.getQuantity() : current + request.getAdjustment();

        if (target < 0) {
            throw new BusinessRuleException("Adjustment would take available stock below zero "
                    + "(currently " + current + ")");
        }

        int delta = target - current;
        stock.setAvailableQuantity(target);

        if (request.getLowStockThreshold() != null) {
            stock.setLowStockThreshold(request.getLowStockThreshold());
        }

        Inventory saved = inventoryRepository.save(stock);

        if (delta != 0) {
            stockMovementRecorder.record(productId, delta, StockMovementReason.ADMIN_ADJUSTMENT, actor);
        }

        return toResponse(saved);
    }

    /**
     * Products at or below the low-stock threshold (spec 9.3). Without an explicit {@code threshold}
     * each product uses its own {@code low_stock_threshold}.
     */
    @Transactional(readOnly = true)
    public InventoryPageResponse findLowStock(Integer threshold, int page, int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), clampPageSize(size));

        Page<Inventory> result = threshold != null
                ? inventoryRepository.findLowStockBelow(threshold, pageable)
                : inventoryRepository.findLowStock(pageable);

        return InventoryPageResponse.builder()
                .content(result.getContent().stream().map(InventoryService::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    private Inventory findStock(Long productId) {

        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No inventory found for product " + productId));
    }

    private int clampPageSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    static InventoryResponse toResponse(Inventory stock) {

        return InventoryResponse.builder()
                .productId(stock.getProductId())
                .sku(stock.getSku())
                .availableQuantity(stock.getAvailableQuantity())
                .reservedQuantity(stock.getReservedQuantity())
                .lowStockThreshold(stock.getLowStockThreshold())
                .lowStock(stock.isBelowThreshold())
                .inStock(stock.isInStock())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }
}
