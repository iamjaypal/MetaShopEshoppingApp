import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map } from 'rxjs';

import { Cart } from '../models/cart.model';

/**
 * The shopper's cart state. Phase F3 owns the add-to-cart action and the
 * navbar badge; the cart page (view, update quantities, remove, validate)
 * completes this service in F4.
 */
@Injectable({ providedIn: 'root' })
export class CartService {

  private readonly http = inject(HttpClient);

  /** Number of items in the cart; drives the navbar badge. */
  private readonly countSignal = signal(0);

  readonly count = this.countSignal;

  /**
   * Adds an item and republishes the cart size from the response.
   * Requires a signed-in user — the interceptor attaches the JWT; guests are
   * redirected to the login page by the calling component.
   */
  addItem(productId: number, quantity = 1): Observable<Cart> {
    return this.http
      .post<Cart>('/api/v1/cart/items', { productId, quantity })
      .pipe(map(cart => {
        this.countSignal.set(cart.totalItems);
        return cart;
      }));
  }

  /** Re-reads the cart from cart-service (used after sign-in / on the page). */
  refresh(): Observable<Cart> {
    return this.http.get<Cart>('/api/v1/cart')
      .pipe(map(cart => {
        this.countSignal.set(cart.totalItems);
        return cart;
      }));
  }
}
