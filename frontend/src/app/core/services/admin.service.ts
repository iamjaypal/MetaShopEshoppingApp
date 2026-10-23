import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApiResponse } from '../models/api-response.model';
import {
  AdminUser, AdminUserPage, Inventory, InventoryPage,
} from '../models/admin.model';
import { Order, OrderPage, OrderStatus, Payment } from '../models/order.model';
import { ProductDetail, ProductPage } from '../models/product.model';

/**
 * The admin console's single API client (`/api/v1/admin/**`). Every call
 * requires the ADMIN role — the interceptor attaches the token; the guard
 * keeps non-admins out of the pages that use this service.
 */
@Injectable({ providedIn: 'root' })
export class AdminService {

  private readonly http = inject(HttpClient);

  // ------------------------------------------------------------- products

  /** All products, including inactive ones. */
  listProducts(page = 0, size = 10, q = '', active: boolean | null = null):
    Observable<ProductPage> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size));

    if (q) {
      params = params.set('q', q);
    }
    if (active !== null) {
      params = params.set('active', String(active));
    }

    return this.http.get<ApiResponse<ProductPage>>('/api/v1/admin/products', { params })
      .pipe(map(response => response.data));
  }

  /** One product with gallery — plain JSON. */
  getProduct(id: number): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`/api/v1/admin/products/${id}`);
  }

  createProduct(request: AdminProductRequest): Observable<ProductDetail> {
    return this.http.post<ProductDetail>('/api/v1/admin/products', request);
  }

  updateProduct(id: number, request: AdminProductRequest): Observable<ProductDetail> {
    return this.http.put<ProductDetail>(`/api/v1/admin/products/${id}`, request);
  }

  setActive(id: number, active: boolean): Observable<ProductDetail> {
    return this.http.patch<ProductDetail>(`/api/v1/admin/products/${id}/status`, { active });
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/admin/products/${id}`);
  }

  /** Uploads an image for a product (multipart). */
  uploadImage(productId: number, file: File): Observable<unknown> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post(`/api/v1/admin/products/${productId}/images`, form);
  }

  deleteImage(productId: number, imageId: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/admin/products/${productId}/images/${imageId}`);
  }

  // ------------------------------------------------------ orders / payments

  listOrders(status: OrderStatus | null, page = 0, size = 10): Observable<OrderPage> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<OrderPage>('/api/v1/admin/orders', { params });
  }

  updateOrderStatus(id: number, status: OrderStatus): Observable<Order> {
    return this.http.post<Order>(`/api/v1/admin/orders/${id}/status`, { status });
  }

  listPayments(page = 0, size = 10): Observable<AdminPaymentPage> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<AdminPaymentPage>('/api/v1/admin/payments', { params });
  }

  // ------------------------------------------------------------- inventory

  listLowStock(threshold = 5, page = 0, size = 20): Observable<InventoryPage> {
    return this.http.get<InventoryPage>('/api/v1/admin/inventory/low-stock', {
      params: { threshold: String(threshold), page: String(page), size: String(size) },
    });
  }

  getStock(productId: number): Observable<Inventory> {
    return this.http.get<Inventory>(`/api/v1/admin/inventory/${productId}`);
  }

  /** Sets an absolute quantity + threshold; 404 when no row exists yet. */
  setStock(productId: number, quantity: number, lowStockThreshold: number):
    Observable<Inventory> {
    return this.http.put<Inventory>(`/api/v1/admin/inventory/${productId}`, {
      quantity,
      lowStockThreshold,
    });
  }

  /** Creates the stock row for a product that has none. */
  createStock(productId: number, sku: string, quantity: number, lowStockThreshold: number):
    Observable<Inventory> {
    return this.http.post<Inventory>('/api/v1/admin/inventory', {
      productId, sku, quantity, lowStockThreshold,
    });
  }

  // ----------------------------------------------------------------- users

  listUsers(page = 0, size = 10): Observable<AdminUserPage> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<ApiResponse<AdminUserPage>>('/api/v1/admin/users', { params })
      .pipe(map(response => response.data));
  }

  setUserEnabled(id: number, enabled: boolean): Observable<AdminUser> {
    return this.http
      .patch<ApiResponse<AdminUser>>(`/api/v1/admin/users/${id}/status`, { enabled })
      .pipe(map(response => response.data));
  }

  // ----------------------------------------------------- brands / categories

  listBrands(): Observable<AdminBrand[]> {
    return this.http.get<ApiResponse<AdminBrand[]>>('/api/v1/admin/brands')
      .pipe(map(response => response.data));
  }

  listCategories(): Observable<AdminCategory[]> {
    return this.http.get<ApiResponse<AdminCategory[]>>('/api/v1/admin/categories')
      .pipe(map(response => response.data));
  }
}

/** Product create/update body (product-service's `ProductRequest`). */
export interface AdminProductRequest {
  name: string;
  sku: string;
  shortDescription: string;
  description: string;
  price: number;
  mrp: number;
  categoryId: number;
  brandId: number;
  active: boolean;
}

export interface AdminBrand {
  id: number;
  name: string;
  slug: string;
  logoUrl: string;
  active: boolean;
}

export interface AdminCategory {
  id: number;
  name: string;
  slug: string;
  description: string;
  parentId: number | null;
  active: boolean;
  children: AdminCategory[];
}

/** The admin payment ledger page (payment-service's `PaymentPageResponse`). */
export interface AdminPaymentPage {
  content: Payment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
