/**
 * Admin console contracts — the `/api/v1/admin/**` endpoints. Page envelopes
 * mirror each service's `*PageResponse` (plain JSON except where noted).
 */

import { OrderStatus, Payment, PaymentStatus } from './order.model';

/** One registered user (auth-service's `AdminUserResponse`). */
export interface AdminUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  enabled: boolean;
  emailVerified: boolean;
  roles: string[];
  createdAt: string;
  updatedAt: string;
}

/** Generic page shape (auth-service's `PageResponse` record). */
export interface AdminUserPage {
  content: AdminUser[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** Full stock row (inventory-service's `InventoryResponse`). */
export interface Inventory {
  productId: number;
  sku: string;
  availableQuantity: number;
  reservedQuantity: number;
  lowStockThreshold: number;
  lowStock: boolean;
  inStock: boolean;
  updatedAt: string;
}

/** One page of stock rows (inventory-service's `InventoryPageResponse`). */
export interface InventoryPage {
  content: Inventory[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** Admin payment row — the ledger (payment-service's `PaymentResponse`). */
export type PaymentRow = Payment;

/** Order status options for the admin status changer. */
export const ORDER_STATUSES: OrderStatus[] = [
  'CREATED', 'CONFIRMED', 'DELIVERED', 'CANCELLED',
];

/** Payment statuses shown in the ledger filter. */
export const PAYMENT_STATUSES: PaymentStatus[] = [
  'PENDING', 'PAID', 'FAILED',
];
