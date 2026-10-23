package com.shopMeta.cart_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.shopMeta.cart_service.client.dto.AvailabilityItem;
import com.shopMeta.cart_service.client.dto.ProductSnapshot;
import com.shopMeta.cart_service.config.CartProperties;
import com.shopMeta.cart_service.dto.AddCartItemRequest;
import com.shopMeta.cart_service.dto.CartIssue;
import com.shopMeta.cart_service.dto.CartIssueType;
import com.shopMeta.cart_service.dto.CartItemResponse;
import com.shopMeta.cart_service.dto.CartResponse;
import com.shopMeta.cart_service.dto.CartValidationResponse;
import com.shopMeta.cart_service.dto.UpdateCartItemRequest;
import com.shopMeta.cart_service.exception.BusinessRuleException;
import com.shopMeta.cart_service.exception.InsufficientStockException;
import com.shopMeta.cart_service.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cart business rules (spec 10).
 *
 * <p>The cart stores only {@code productId} + {@code quantity}. Every response is enriched with the
 * live catalogue price and the live stock level, because a cart that shows a stale price is
 * misleading — the price is only frozen when the order is placed (spec 10.2).
 *
 * <p>The owner is always the authenticated caller: the user id comes from the access token, never
 * from the request (spec 10.3 / 18). Because every line is looked up inside that caller's own cart,
 * somebody else's item id is indistinguishable from a non-existent one and yields a 404.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartStore cartStore;
    private final CatalogClient catalogClient;
    private final CartProperties properties;

    // ------------------------------------------------------------------ read

    /** The caller's cart with live prices, line totals, subtotal and stock warnings. */
    public CartResponse getCart(Long userId) {
        return render(cartStore.load(userId), userId);
    }

    // ----------------------------------------------------------------- writes

    /**
     * Adds a product to the cart, or increases its quantity (spec 10.3).
     *
     * <p>The product must exist, be active and be in stock for the <em>resulting</em> quantity, so
     * the cart can never hold a line that is already impossible to order.
     */
    public CartResponse addItem(Long userId, AddCartItemRequest request) {

        Long productId = request.getProductId();
        int quantity = request.getQuantity();

        CartData current = cartStore.load(userId);
        int existingQuantity = quantityOf(current, productId);
        int newQuantity = existingQuantity + quantity;

        if (newQuantity > properties.getMaxQuantityPerItem()) {
            throw new BusinessRuleException("You can have at most "
                    + properties.getMaxQuantityPerItem() + " of the same product in your cart"
                    + (existingQuantity > 0 ? " (it already holds " + existingQuantity + ")" : ""));
        }
        if (existingQuantity == 0
                && current != null
                && current.lines().size() >= properties.getMaxDistinctItems()) {
            throw new BusinessRuleException("Your cart cannot hold more than "
                    + properties.getMaxDistinctItems() + " different products");
        }

        // Spec 10.3: validate the product is active and the resulting line is in stock.
        ProductSnapshot product = requirePurchasableProduct(productId);
        requireAvailableStock(productId, newQuantity);

        try {
            cartStore.addItem(userId, productId, quantity, product.getPrice());
        } catch (DataIntegrityViolationException ex) {
            // Two adds raced to create the same cart or the same line. The unique keys let exactly
            // one win; a single retry now finds the winner's row and takes the update branch.
            log.debug("Concurrent cart write for user {}; retrying once", userId);
            cartStore.addItem(userId, productId, quantity, product.getPrice());
        }

        return getCart(userId);
    }

    /** Changes a line to an absolute quantity, re-validating it against the live catalogue. */
    public CartResponse updateItem(Long userId, Long itemId, UpdateCartItemRequest request) {

        CartData current = cartStore.load(userId);
        CartData.Line line = requireOwnedLine(current, itemId);
        int quantity = request.getQuantity();

        requirePurchasableProduct(line.productId());
        requireAvailableStock(line.productId(), quantity);

        cartStore.updateQuantity(current.cartId(), itemId, quantity);

        return getCart(userId);
    }

    /** Removes one line from the caller's cart. */
    public CartResponse removeItem(Long userId, Long itemId) {

        CartData current = cartStore.load(userId);
        requireOwnedLine(current, itemId);

        cartStore.removeItem(current.cartId(), itemId);

        return getCart(userId);
    }

    /** Empties the cart but keeps it, so the next add does not have to create it again. */
    public CartResponse clearCart(Long userId) {

        CartData current = cartStore.load(userId);

        if (current != null) {
            cartStore.clearItems(current.cartId());
        }

        return getCart(userId);
    }

    /**
     * Pre-checkout check (spec 10.3): is every line still active, in stock and within the quantity
     * limits? Always 200 — an invalid cart is a valid answer, not an error. Order-service repeats
     * the check authoritatively when it reserves stock.
     */
    public CartValidationResponse validate(Long userId) {

        CartData current = cartStore.load(userId);

        if (current == null || current.lines().isEmpty()) {
            return CartValidationResponse.builder()
                    .valid(false)
                    .itemCount(0)
                    .issues(List.of(CartIssue.builder()
                            .type(CartIssueType.CART_EMPTY)
                            .message("Your cart is empty")
                            .build()))
                    .checkedAt(LocalDateTime.now())
                    .build();
        }

        Map<Long, Integer> requested = requestedQuantities(current);
        Map<Long, ProductSnapshot> products = catalogClient.fetchProducts(requested.keySet());
        Map<Long, AvailabilityItem> availability = catalogClient.fetchAvailability(requested);

        List<CartIssue> issues = new ArrayList<>();

        for (CartData.Line line : current.lines()) {

            ProductSnapshot product = products.get(line.productId());
            AvailabilityItem stock = availability.get(line.productId());

            if (product == null) {
                issues.add(issue(line, CartIssueType.PRODUCT_NOT_FOUND,
                        "A product in your cart is no longer in the catalogue"));
                continue;
            }

            if (!Boolean.TRUE.equals(product.getActive())) {
                issues.add(issue(line, CartIssueType.PRODUCT_INACTIVE,
                        product.getName() + " is no longer available for purchase"));
            }

            if (line.quantity() > properties.getMaxQuantityPerItem()) {
                issues.add(issue(line, CartIssueType.QUANTITY_LIMIT_EXCEEDED,
                        "You can order at most " + properties.getMaxQuantityPerItem()
                                + " of " + product.getName()));
            }

            if (stock == null || !stock.isInStock()) {
                issues.add(issue(line, CartIssueType.OUT_OF_STOCK,
                        product.getName() + " is out of stock"));
            } else if (!stock.isSufficient()) {
                issues.add(issue(line, CartIssueType.INSUFFICIENT_STOCK,
                        "Only " + stock.getAvailable() + " of " + product.getName()
                                + " left in stock"));
            }
        }

        return CartValidationResponse.builder()
                .valid(issues.isEmpty())
                .itemCount(current.lines().size())
                .issues(issues)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    // ---------------------------------------------------------------- internal

    /**
     * Clears a user's cart after their order was confirmed (spec 10.3). Idempotent, so a replayed
     * {@code OrderConfirmedEvent} is harmless.
     */
    public void clearCartForUser(Long userId) {

        CartData current = cartStore.load(userId);

        if (current != null) {
            cartStore.clearItems(current.cartId());
        }
    }

    // ----------------------------------------------------------------- helpers

    /** Merges the stored lines with live catalogue and stock data into the API response. */
    private CartResponse render(CartData data, Long userId) {

        if (data == null || data.lines().isEmpty()) {
            return CartResponse.empty(userId, data == null ? null : data.cartId());
        }

        Map<Long, Integer> requested = requestedQuantities(data);
        Map<Long, ProductSnapshot> products = catalogClient.fetchProducts(requested.keySet());
        Map<Long, AvailabilityItem> availability = catalogClient.fetchAvailability(requested);

        List<CartItemResponse> items = new ArrayList<>(data.lines().size());
        List<String> warnings = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItems = 0;

        for (CartData.Line line : data.lines()) {

            ProductSnapshot product = products.get(line.productId());
            AvailabilityItem stock = availability.get(line.productId());

            BigDecimal unitPrice = product != null && product.getPrice() != null
                    ? product.getPrice()
                    : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(line.quantity()));

            String warning = warningFor(product, stock, line.quantity());
            if (warning != null) {
                warnings.add(warning);
            }

            items.add(CartItemResponse.builder()
                    .itemId(line.itemId())
                    .productId(line.productId())
                    .sku(product != null ? product.getSku() : null)
                    .name(product != null ? product.getName() : null)
                    .imageUrl(product != null ? product.getImageUrl() : null)
                    .unitPrice(unitPrice)
                    .addedPrice(line.addedPrice())
                    .priceChanged(line.addedPrice() != null
                            && unitPrice.compareTo(line.addedPrice()) != 0)
                    .quantity(line.quantity())
                    .lineTotal(lineTotal)
                    .availableQuantity(stock != null ? stock.getAvailable() : 0)
                    .inStock(stock != null && stock.isInStock())
                    .sufficient(stock != null && stock.isSufficient())
                    .productActive(product != null && Boolean.TRUE.equals(product.getActive()))
                    .warning(warning)
                    .build());

            subtotal = subtotal.add(lineTotal);
            totalItems += line.quantity();
        }

        return CartResponse.builder()
                .cartId(data.cartId())
                .userId(userId)
                .items(items)
                .totalItems(totalItems)
                .distinctItems(items.size())
                .subtotal(subtotal)
                .currency("INR")
                .hasWarnings(!warnings.isEmpty())
                .warnings(warnings)
                .updatedAt(data.updatedAt())
                .build();
    }

    /**
     * Why a line cannot be ordered right now, or {@code null} when it is fine. Stock warnings are
     * the reason the cart can hold an item that later went out of stock: it is flagged, not silently
     * dropped.
     */
    private String warningFor(ProductSnapshot product, AvailabilityItem stock, int quantity) {

        if (product == null) {
            return "A product in your cart is no longer in the catalogue";
        }
        if (!Boolean.TRUE.equals(product.getActive())) {
            return product.getName() + " is no longer available for purchase";
        }
        if (quantity > properties.getMaxQuantityPerItem()) {
            return "You can order at most " + properties.getMaxQuantityPerItem()
                    + " of " + product.getName();
        }
        if (stock == null || !stock.isInStock()) {
            return product.getName() + " is out of stock";
        }
        if (!stock.isSufficient()) {
            return "Only " + stock.getAvailable() + " of " + product.getName()
                    + " left in stock";
        }
        return null;
    }

    private ProductSnapshot requirePurchasableProduct(Long productId) {

        ProductSnapshot product = catalogClient.fetchProducts(List.of(productId)).get(productId);

        if (product == null) {
            throw new ResourceNotFoundException("Product " + productId + " does not exist");
        }
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new BusinessRuleException(
                    "Product " + productId + " is no longer available for purchase");
        }
        return product;
    }

    private void requireAvailableStock(Long productId, int quantity) {

        AvailabilityItem stock = catalogClient
                .fetchAvailability(Map.of(productId, quantity))
                .get(productId);

        if (stock == null || !stock.isInStock()) {
            throw new InsufficientStockException(
                    "Product " + productId + " is out of stock", productId, 0);
        }
        if (!stock.isSufficient()) {
            throw new InsufficientStockException(
                    "Only " + stock.getAvailable() + " unit(s) of product " + productId
                            + " are available",
                    productId, stock.getAvailable());
        }
    }

    /** The line with this id inside the caller's own cart, or a 404. */
    private static CartData.Line requireOwnedLine(CartData data, Long itemId) {

        if (data != null) {
            for (CartData.Line line : data.lines()) {
                if (line.itemId().equals(itemId)) {
                    return line;
                }
            }
        }

        throw new ResourceNotFoundException(
                "Cart item " + itemId + " was not found in your cart");
    }

    private static int quantityOf(CartData data, Long productId) {

        if (data == null) {
            return 0;
        }
        return data.lines().stream()
                .filter(line -> line.productId().equals(productId))
                .mapToInt(CartData.Line::quantity)
                .sum();
    }

    /** How many of each product the cart needs, to ask the downstream services in one call. */
    private static Map<Long, Integer> requestedQuantities(CartData data) {

        Map<Long, Integer> requested = new LinkedHashMap<>();
        data.lines().forEach(line ->
                requested.merge(line.productId(), line.quantity(), Integer::sum));
        return requested;
    }

    private static CartIssue issue(CartData.Line line, CartIssueType type, String message) {

        return CartIssue.builder()
                .itemId(line.itemId())
                .productId(line.productId())
                .type(type)
                .message(message)
                .build();
    }
}
