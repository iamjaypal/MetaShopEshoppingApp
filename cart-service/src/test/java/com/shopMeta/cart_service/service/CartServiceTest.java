package com.shopMeta.cart_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.shopMeta.cart_service.client.dto.AvailabilityItem;
import com.shopMeta.cart_service.client.dto.ProductSnapshot;
import com.shopMeta.cart_service.config.CartProperties;
import com.shopMeta.cart_service.dto.AddCartItemRequest;
import com.shopMeta.cart_service.dto.CartIssueType;
import com.shopMeta.cart_service.dto.CartResponse;
import com.shopMeta.cart_service.dto.CartValidationResponse;
import com.shopMeta.cart_service.dto.UpdateCartItemRequest;
import com.shopMeta.cart_service.exception.BusinessRuleException;
import com.shopMeta.cart_service.exception.InsufficientStockException;
import com.shopMeta.cart_service.exception.ResourceNotFoundException;

/**
 * Unit tests of the cart business rules: quantity caps, purchaseability checks, stock
 * sufficiency, the add-race retry and the pre-checkout validation.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 50L;
    private static final Long ITEM_ID = 7L;

    @Mock
    private CartStore cartStore;

    @Mock
    private CatalogClient catalogClient;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        CartProperties properties = new CartProperties();
        cartService = new CartService(cartStore, catalogClient, properties);
    }

    // ------------------------------------------------------------------ fixtures

    private CartData.Line line(Long itemId, Long productId, int quantity, String addedPrice) {
        return new CartData.Line(itemId, productId, quantity,
                addedPrice == null ? null : new BigDecimal(addedPrice),
                LocalDateTime.now().minusHours(1));
    }

    private CartData cartWith(CartData.Line... lines) {
        return new CartData(3L, USER_ID, LocalDateTime.now().minusMinutes(1), List.of(lines));
    }

    private ProductSnapshot product(Long id, String name, String price, boolean active) {
        return ProductSnapshot.builder()
                .id(id)
                .sku("SKU-" + id)
                .name(name)
                .price(price == null ? null : new BigDecimal(price))
                .imageUrl("/uploads/products/" + id + ".png")
                .active(active)
                .build();
    }

    private AvailabilityItem stock(Long productId, int available, int requested,
            boolean sufficient, boolean inStock) {
        return new AvailabilityItem(productId, "SKU-" + productId, available, requested,
                sufficient, inStock);
    }

    /** The two downstream reads a render makes: one product, one stock line. */
    private void givenCatalogue(ProductSnapshot product, AvailabilityItem stock) {
        given(catalogClient.fetchProducts(any())).willReturn(Map.of(product.getId(), product));
        given(catalogClient.fetchAvailability(anyMap()))
                .willReturn(Map.of(product.getId(), stock));
    }

    private void givenNothingInCatalogue() {
        given(catalogClient.fetchProducts(any())).willReturn(Map.of());
        given(catalogClient.fetchAvailability(anyMap())).willReturn(Map.of());
    }

    // ------------------------------------------------------------------ read

    @Test
    @DisplayName("getCart: a user who never added anything gets an empty cart, not a 404")
    void getCart_unknownUserIsEmpty() {

        given(cartStore.load(USER_ID)).willReturn(null);

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalItems()).isZero();
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getCartId()).isNull();
    }

    @Test
    @DisplayName("getCart: renders live prices, line totals, subtotal and stock flags")
    void getCart_rendersLiveCatalogue() {

        given(cartStore.load(USER_ID)).willReturn(cartWith(line(ITEM_ID, PRODUCT_ID, 2, "79999.00")));
        givenCatalogue(product(PRODUCT_ID, "iPhone 15", "79999.00", true),
                stock(PRODUCT_ID, 25, 2, true, true));

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("79999.00");
        assertThat(response.getItems().get(0).getLineTotal()).isEqualByComparingTo("159998.00");
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(response.getItems().get(0).isInStock()).isTrue();
        assertThat(response.getItems().get(0).isSufficient()).isTrue();
        assertThat(response.getItems().get(0).isPriceChanged()).isFalse();
        assertThat(response.getItems().get(0).getWarning()).isNull();
        assertThat(response.getSubtotal()).isEqualByComparingTo("159998.00");
        assertThat(response.getTotalItems()).isEqualTo(2);
        assertThat(response.getDistinctItems()).isEqualTo(1);
        assertThat(response.isHasWarnings()).isFalse();
    }

    @Test
    @DisplayName("getCart: flags a line whose price changed since it was added")
    void getCart_flagsPriceChange() {

        given(cartStore.load(USER_ID)).willReturn(cartWith(line(ITEM_ID, PRODUCT_ID, 1, "89999.00")));
        givenCatalogue(product(PRODUCT_ID, "iPhone 15", "79999.00", true),
                stock(PRODUCT_ID, 25, 1, true, true));

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.getItems().get(0).isPriceChanged()).isTrue();
        assertThat(response.getItems().get(0).getAddedPrice()).isEqualByComparingTo("89999.00");
    }

    @Test
    @DisplayName("getCart: a product that vanished from the catalogue is flagged, not silently dropped")
    void getCart_flagsVanishedProduct() {

        given(cartStore.load(USER_ID)).willReturn(cartWith(line(ITEM_ID, PRODUCT_ID, 1, "99.00")));
        givenNothingInCatalogue();

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).isProductActive()).isFalse();
        assertThat(response.getItems().get(0).getWarning())
                .contains("no longer in the catalogue");
        assertThat(response.isHasWarnings()).isTrue();
    }

    @Test
    @DisplayName("getCart: too little stock becomes a warning with the real number")
    void getCart_flagsInsufficientStock() {

        given(cartStore.load(USER_ID)).willReturn(cartWith(line(ITEM_ID, PRODUCT_ID, 5, "99.00")));
        givenCatalogue(product(PRODUCT_ID, "Keyboard", "99.00", true),
                stock(PRODUCT_ID, 2, 5, false, true));

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.getItems().get(0).isSufficient()).isFalse();
        assertThat(response.getItems().get(0).getWarning()).contains("Only 2");
        assertThat(response.isHasWarnings()).isTrue();
    }

    // ------------------------------------------------------------------ add

    @Test
    @DisplayName("add: validates the product and the resulting quantity, then stores the line")
    void add_storesValidatedLine() {

        given(cartStore.load(USER_ID)).willReturn(null);
        given(catalogClient.fetchProducts(any()))
                .willReturn(Map.of(PRODUCT_ID, product(PRODUCT_ID, "iPhone 15", "79999.00", true)));
        given(catalogClient.fetchAvailability(anyMap()))
                .willReturn(Map.of(PRODUCT_ID, stock(PRODUCT_ID, 25, 3, true, true)));
        // The render after the store call asks the catalogue again.
        given(cartStore.load(USER_ID)).willReturn(null);

        CartResponse response = cartService.addItem(USER_ID,
                AddCartItemRequest.builder().productId(PRODUCT_ID).quantity(3).build());

        then(cartStore).should().addItem(USER_ID, PRODUCT_ID, 3, new BigDecimal("79999.00"));
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("add: merging into an existing line counts what is already in the cart")
    void add_mergesWithExistingLine() {

        given(cartStore.load(USER_ID))
                .willReturn(cartWith(line(ITEM_ID, PRODUCT_ID, 18, "79999.00")));

        // 18 already in the cart + 5 wanted = 23 > 20 → refused before any stock is checked.
        assertThatThrownBy(() -> cartService.addItem(USER_ID,
                AddCartItemRequest.builder().productId(PRODUCT_ID).quantity(5).build()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at most 20");

        then(cartStore).should(never()).addItem(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("add: a full cart refuses one more distinct product")
    void add_enforcesDistinctLimit() {

        CartData.Line[] lines = new CartData.Line[50];
        for (int i = 0; i < 50; i++) {
            // Product ids that cannot collide with the product being added below.
            lines[i] = line((long) (1000 + i), (long) (1000 + i), 1, "10.00");
        }
        given(cartStore.load(USER_ID)).willReturn(cartWith(lines));

        assertThatThrownBy(() -> cartService.addItem(USER_ID,
                AddCartItemRequest.builder().productId(PRODUCT_ID).quantity(1).build()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("50");
    }

    @Test
    @DisplayName("add: an unknown product is a 404")
    void add_unknownProductIsNotFound() {

        given(cartStore.load(USER_ID)).willReturn(null);
        given(catalogClient.fetchProducts(any())).willReturn(Map.of());

        assertThatThrownBy(() -> cartService.addItem(USER_ID,
                AddCartItemRequest.builder().productId(999L).quantity(1).build()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("add: a deactivated product is a 422")
    void add_inactiveProductIsUnprocessable() {

        given(cartStore.load(USER_ID)).willReturn(null);
        given(catalogClient.fetchProducts(any()))
                .willReturn(Map.of(PRODUCT_ID, product(PRODUCT_ID, "Old phone", "4999.00", false)));

        assertThatThrownBy(() -> cartService.addItem(USER_ID,
                AddCartItemRequest.builder().productId(PRODUCT_ID).quantity(1).build()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    @DisplayName("add: not enough stock is a 409 that names the product and the real number")
    void add_insufficientStockIsConflict() {

        given(cartStore.load(USER_ID)).willReturn(null);
        given(catalogClient.fetchProducts(any()))
                .willReturn(Map.of(PRODUCT_ID, product(PRODUCT_ID, "Keyboard", "99.00", true)));
        given(catalogClient.fetchAvailability(anyMap()))
                .willReturn(Map.of(PRODUCT_ID, stock(PRODUCT_ID, 2, 5, false, true)));

        assertThatThrownBy(() -> cartService.addItem(USER_ID,
                AddCartItemRequest.builder().productId(PRODUCT_ID).quantity(5).build()))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("2")
                .satisfies(error -> assertThat(
                        ((InsufficientStockException) error).getProductId()).isEqualTo(PRODUCT_ID));
    }

    @Test
    @DisplayName("add: a concurrent double-add is retried once and lands on the update branch")
    void add_retriesOnUniqueKeyRace() {

        given(cartStore.load(USER_ID)).willReturn(null);
        given(catalogClient.fetchProducts(any()))
                .willReturn(Map.of(PRODUCT_ID, product(PRODUCT_ID, "iPhone 15", "79999.00", true)));
        given(catalogClient.fetchAvailability(anyMap()))
                .willReturn(Map.of(PRODUCT_ID, stock(PRODUCT_ID, 25, 1, true, true)));
        willThrow(new DataIntegrityViolationException("duplicate"))
                .willDoNothing()
                .given(cartStore).addItem(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());

        cartService.addItem(USER_ID,
                AddCartItemRequest.builder().productId(PRODUCT_ID).quantity(1).build());

        then(cartStore).should(times(2)).addItem(USER_ID, PRODUCT_ID, 1, new BigDecimal("79999.00"));
    }

    // ------------------------------------------------------------------ update / remove / clear

    @Test
    @DisplayName("update: sets the absolute quantity after re-checking the catalogue")
    void update_setsAbsoluteQuantity() {

        given(cartStore.load(USER_ID))
                .willReturn(cartWith(line(ITEM_ID, PRODUCT_ID, 2, "99.00")));
        given(catalogClient.fetchProducts(any()))
                .willReturn(Map.of(PRODUCT_ID, product(PRODUCT_ID, "Keyboard", "99.00", true)));
        given(catalogClient.fetchAvailability(anyMap()))
                .willReturn(Map.of(PRODUCT_ID, stock(PRODUCT_ID, 25, 4, true, true)));

        cartService.updateItem(USER_ID, ITEM_ID, UpdateCartItemRequest.builder().quantity(4).build());

        then(cartStore).should().updateQuantity(3L, ITEM_ID, 4);
    }

    @Test
    @DisplayName("update: somebody else's itemId is indistinguishable from a missing one — 404")
    void update_foreignItemIsNotFound() {

        given(cartStore.load(USER_ID)).willReturn(cartWith(line(ITEM_ID, PRODUCT_ID, 2, "99.00")));

        assertThatThrownBy(() -> cartService.updateItem(USER_ID, 999L,
                UpdateCartItemRequest.builder().quantity(4).build()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        then(cartStore).should(never()).updateQuantity(anyLong(), anyLong(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("remove: deletes the caller's own line")
    void remove_deletesLine() {

        given(cartStore.load(USER_ID))
                .willReturn(cartWith(line(ITEM_ID, PRODUCT_ID, 2, "99.00")));

        cartService.removeItem(USER_ID, ITEM_ID);

        then(cartStore).should().removeItem(3L, ITEM_ID);
    }

    @Test
    @DisplayName("remove: a line that is not in the cart is a 404")
    void remove_unknownItemIsNotFound() {

        given(cartStore.load(USER_ID)).willReturn(null);

        assertThatThrownBy(() -> cartService.removeItem(USER_ID, ITEM_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("clear: empties the cart but keeps it for the next add")
    void clear_emptiesButKeepsTheCart() {

        given(cartStore.load(USER_ID))
                .willReturn(cartWith(line(ITEM_ID, PRODUCT_ID, 2, "99.00")));

        cartService.clearCart(USER_ID);

        then(cartStore).should().clearItems(3L);
    }

    // ------------------------------------------------------------------ validate

    @Test
    @DisplayName("validate: an empty cart is invalid with a CART_EMPTY issue")
    void validate_emptyCartIsInvalid() {

        given(cartStore.load(USER_ID)).willReturn(null);

        CartValidationResponse response = cartService.validate(USER_ID);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getIssues()).singleElement()
                .satisfies(issue -> assertThat(issue.getType()).isEqualTo(CartIssueType.CART_EMPTY));
    }

    @Test
    @DisplayName("validate: a healthy cart is valid with no issues")
    void validate_healthyCartIsValid() {

        given(cartStore.load(USER_ID)).willReturn(cartWith(line(ITEM_ID, PRODUCT_ID, 2, "99.00")));
        givenCatalogue(product(PRODUCT_ID, "Keyboard", "99.00", true),
                stock(PRODUCT_ID, 25, 2, true, true));

        assertThat(cartService.validate(USER_ID).isValid()).isTrue();
    }

    @Test
    @DisplayName("validate: reports inactive and out-of-stock lines as issues")
    void validate_reportsEveryIssue() {

        given(cartStore.load(USER_ID)).willReturn(cartWith(
                line(10L, PRODUCT_ID, 2, "99.00"),
                line(11L, 51L, 1, "199.00")));
        given(catalogClient.fetchProducts(any())).willReturn(Map.of(
                PRODUCT_ID, product(PRODUCT_ID, "Keyboard", "99.00", false),
                51L, product(51L, "Mouse", "199.00", true)));
        given(catalogClient.fetchAvailability(anyMap())).willReturn(Map.of(
                PRODUCT_ID, stock(PRODUCT_ID, 25, 2, true, true),
                51L, stock(51L, 0, 1, false, false)));

        CartValidationResponse response = cartService.validate(USER_ID);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getIssues()).extracting(com.shopMeta.cart_service.dto.CartIssue::getType)
                .containsExactlyInAnyOrder(CartIssueType.PRODUCT_INACTIVE, CartIssueType.OUT_OF_STOCK);
    }
}
