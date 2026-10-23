import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of } from 'rxjs';

import { ApiError } from '../models/api-error';
import { InventoryView } from '../models/product.model';

/**
 * Public stock reads from inventory-service (spec 9). Used by the product
 * page today; the cart/checkout pages reuse it in later phases.
 */
@Injectable({ providedIn: 'root' })
export class InventoryService {

  private readonly http = inject(HttpClient);

  /**
   * How many units are sellable for a product. A product without an inventory
   * row answers 404 — that is "not purchasable", so it is normalised to an
   * out-of-stock view instead of an error.
   */
  getStock(productId: number | string): Observable<InventoryView> {
    return this.http.get<InventoryView>(`/api/v1/inventory/${productId}`)
      .pipe(catchError((error: ApiError) => {
        if (error.status === 404) {
          return of({ productId: Number(productId), inStock: false, availableQuantity: 0 });
        }
        throw error;
      }));
  }
}
