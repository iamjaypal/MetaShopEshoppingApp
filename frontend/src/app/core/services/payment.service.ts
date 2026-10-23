import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse } from '../models/api-response.model';
import { Payment, PaymentMethod } from '../models/order.model';

/**
 * Payments (spec 12): pay for an order through the mock gateway.
 *
 * The two outcomes are intentional shapes, not HTTP errors:
 * - captured  → 201, envelope `success: true`
 * - declined  → 200, envelope `success: false` with the failure reason
 * (the attempt is recorded in the ledger either way), so the caller reads
 * `response.success` instead of catching an ApiError.
 */
@Injectable({ providedIn: 'root' })
export class PaymentService {

  private readonly http = inject(HttpClient);

  /**
   * Pays for an order. A captured charge confirms the order (stock sold,
   * cart cleared); a declined charge cancels it (stock released).
   */
  pay(orderId: number, method: PaymentMethod): Observable<ApiResponse<Payment>> {
    return this.http.post<ApiResponse<Payment>>('/api/v1/payments', {
      orderId,
      method,
    });
  }
}
