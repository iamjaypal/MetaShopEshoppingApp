import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApiResponse } from '../models/api-response.model';
import {
  Brand, Category, ProductDetail, ProductPage, ProductQuery,
} from '../models/product.model';

/**
 * Catalogue reads from product-service (spec 8): listing with filters, the
 * product detail, categories and brands.
 */
@Injectable({ providedIn: 'root' })
export class ProductService {

  private readonly http = inject(HttpClient);

  /**
   * One page of the listing. Empty/undefined query values are skipped, so
   * callers can pass their whole filter state blindly.
   */
  getProducts(query: ProductQuery = {}): Observable<ProductPage> {
    let params = new HttpParams();

    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }

    return this.http
      .get<ApiResponse<ProductPage>>('/api/v1/products', { params })
      .pipe(map(response => response.data));
  }

  /** One product with gallery + attributes — plain JSON, no envelope. */
  getProduct(id: number | string): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`/api/v1/products/${id}`);
  }

  /** The active category tree (top-level categories for the filter panel). */
  getCategories(): Observable<Category[]> {
    return this.http
      .get<ApiResponse<Category[]>>('/api/v1/categories')
      .pipe(map(response => response.data));
  }

  /** All brands — for the filter panel. */
  getBrands(): Observable<Brand[]> {
    return this.http
      .get<ApiResponse<Brand[]>>('/api/v1/brands')
      .pipe(map(response => response.data));
  }
}
