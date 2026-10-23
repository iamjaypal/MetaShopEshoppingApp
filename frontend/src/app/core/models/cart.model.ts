/**
 * Cart contracts, mirroring cart-service's DTOs (`CartResponse`,
 * `CartItemResponse`).
 */

/** One line in the cart. */
export interface CartItem {
  itemId: number;
  productId: number;
  sku: string;
  name: string;
  imageUrl: string;
  unitPrice: number;
  addedPrice: number;
  priceChanged: boolean;
  quantity: number;
  lineTotal: number;
  availableQuantity: number;
  inStock: boolean;
}

/** The whole cart — cart-service returns this as plain JSON. */
export interface Cart {
  cartId: number;
  userId: number;
  items: CartItem[];
  totalItems: number;
  distinctItems: number;
  subtotal: number;
  currency: string;
  hasWarnings: boolean;
  warnings: string[];
  updatedAt: string;
}
