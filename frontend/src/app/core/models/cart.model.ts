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

/** One reason the cart cannot be checked out (cart-service's CartIssue). */
export interface CartIssue {
  itemId: number | null;
  productId: number | null;
  type: string;
  message: string;
}

/** Answer of `POST /api/v1/cart/validate` — always 200, even when invalid. */
export interface CartValidation {
  valid: boolean;
  itemCount: number;
  issues: CartIssue[];
  checkedAt: string;
}
