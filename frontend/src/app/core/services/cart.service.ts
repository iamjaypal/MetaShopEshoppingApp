import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map } from 'rxjs';

import { Cart, CartValidation } from '../models/cart.model';

/**
 * The shopper's cart state (spec 10.3). Every cart-service call answers with
 * the whole cart, so the navbar badge stays in sync from one round trip.
 */
@Injectable({ providedIn: 'root' })
export class CartService {

  private readonly http = inject(HttpClient);

  /** Number of units in the cart; drives the navbar badge. */
  private readonly countSignal = signal(0);

  readonly count = this.countSignal;

  /** The cart with live prices and stock warnings. */
  getCart(): Observable<Cart> {
    return this.http.get<Cart>('/api/v1/cart')
      .pipe(map(cart => this.syncCount(cart)));
  }

  /**
   * Adds a product (or increases an existing line). Requires a signed-in
   * user — the interceptor attaches the JWT; guests are redirected to the
   * login page by the calling component.
   */
  addItem(productId: number, quantity = 1): Observable<Cart> {
    return this.http.post<Cart>('/api/v1/cart/items', { productId, quantity })
      .pipe(map(cart => this.syncCount(cart)));
  }

  /** Sets a line to an absolute quantity (1..20 on the backend). */
  updateItem(itemId: number, quantity: number): Observable<Cart> {
    return this.http.put<Cart>(`/api/v1/cart/items/${itemId}`, { quantity })
      .pipe(map(cart => this.syncCount(cart)));
  }

  /** Removes one line and answers with the updated cart. */
  removeItem(itemId: number): Observable<Cart> {
    return this.http.delete<Cart>(`/api/v1/cart/items/${itemId}`)
      .pipe(map(cart => this.syncCount(cart)));
  }

  /** Empties the cart. Idempotent on the backend. */
  clearCart(): Observable<Cart> {
    return this.http.delete<Cart>('/api/v1/cart')
      .pipe(map(cart => this.syncCount(cart)));
  }

  /** Pre-checkout check: reports every line that cannot be bought. */
  validate(): Observable<CartValidation> {
    return this.http.post<CartValidation>('/api/v1/cart/validate', {});
  }

  /** Zeroes the badge after sign-out without a server call. */
  reset(): void {
    this.countSignal.set(0);
  }

  private syncCount(cart: Cart): Cart {
    this.countSignal.set(cart.totalItems);
    return cart;
  }
}
