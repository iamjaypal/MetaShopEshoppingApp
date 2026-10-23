import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Order, OrderPage } from '../models/order.model';

/**
 * The caller's orders (spec 11): checkout, the order history and cancelling
 * an unpaid order. Order endpoints answer with plain JSON — no envelope.
 */
@Injectable({ providedIn: 'root' })
export class OrderService {

  private readonly http = inject(HttpClient);

  /**
   * Checkout: turns the caller's cart into an order, freezes prices and the
   * shipping address, reserves stock. Answers 201 with the order in CREATED.
   */
  createOrder(shippingAddressId: number): Observable<Order> {
    return this.http.post<Order>('/api/v1/orders', { shippingAddressId });
  }

  /** The caller's orders, newest first. */
  getOrders(page = 0, size = 10): Observable<OrderPage> {
    return this.http.get<OrderPage>('/api/v1/orders', {
      params: { page: String(page), size: String(size) },
    });
  }

  /** One order — the owner (or an admin). */
  getOrder(id: number): Observable<Order> {
    return this.http.get<Order>(`/api/v1/orders/${id}`);
  }

  /** Cancels an unpaid order; the reserved stock is released. */
  cancelOrder(id: number): Observable<Order> {
    return this.http.post<Order>(`/api/v1/orders/${id}/cancel`, {});
  }
}
