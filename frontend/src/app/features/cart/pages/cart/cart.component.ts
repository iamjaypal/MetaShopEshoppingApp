import {
  ChangeDetectionStrategy, Component, computed, inject, signal,
} from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Subscription, timer } from 'rxjs';

import { ApiError } from '../../../../core/models/api-error';
import { Cart, CartItem } from '../../../../core/models/cart.model';
import { CartService } from '../../../../core/services/cart.service';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';
import { ToastService } from '../../../../core/services/toast.service';

/** Order rules mirrored from order-service (spec 11) for the summary hints. */
const FREE_SHIPPING_THRESHOLD = 499;
const SHIPPING_FEE = 49;
const MAX_LINE_QUANTITY = 20;

/**
 * The cart page: lines with quantity steppers and remove, live stock
 * warnings, a free-shipping progress bar and the checkout hand-off.
 */
@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [CurrencyPipe, RouterLink, SkeletonComponent],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CartPageComponent {

  private readonly cartService = inject(CartService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly cart = signal<Cart | null>(null);

  /** The cart line currently being changed by the server round trip. */
  readonly pendingItemId = signal<number | null>(null);
  readonly removingItemId = signal<number | null>(null);
  readonly clearing = signal(false);

  /** Two-step clear: first click arms it, the second confirms. */
  readonly clearArmed = signal(false);

  readonly freeShippingThreshold = FREE_SHIPPING_THRESHOLD;
  readonly shippingFee = SHIPPING_FEE;

  readonly freeShippingProgress = computed(() => {
    const subtotal = this.cart()?.subtotal ?? 0;
    return Math.min(100, Math.round((subtotal / FREE_SHIPPING_THRESHOLD) * 100));
  });

  readonly amountToFreeShipping = computed(() => {
    const subtotal = this.cart()?.subtotal ?? 0;
    return Math.max(0, FREE_SHIPPING_THRESHOLD - subtotal);
  });

  readonly hasFreeShipping = computed(() =>
    (this.cart()?.subtotal ?? 0) >= FREE_SHIPPING_THRESHOLD);

  private clearTimer: Subscription | null = null;

  constructor() {
    this.load();
  }

  // ------------------------------------------------------------- loading

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);

    this.cartService.getCart().subscribe({
      next: cart => {
        this.cart.set(cart);
        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.loadError.set(error.message);
        this.loading.set(false);
      },
    });
  }

  // ------------------------------------------------------------ handlers

  /** Steps a line by ±1, clamped to stock and the per-line limit. */
  changeQuantity(item: CartItem, delta: number): void {
    const max = this.maxQuantity(item);
    const next = item.quantity + delta;
    if (next < 1 || next > max || this.pendingItemId() !== null) {
      return;
    }

    this.pendingItemId.set(item.itemId);
    this.cartService.updateItem(item.itemId, next).subscribe({
      next: cart => {
        this.cart.set(cart);
        this.pendingItemId.set(null);
      },
      error: (error: ApiError) => {
        this.pendingItemId.set(null);
        this.toastService.error(error.message);
      },
    });
  }

  maxQuantity(item: CartItem): number {
    if (!item.inStock || item.availableQuantity <= 0) {
      return 0;
    }
    return Math.min(MAX_LINE_QUANTITY, item.availableQuantity);
  }

  removeItem(item: CartItem): void {
    if (this.removingItemId() !== null) {
      return;
    }
    this.removingItemId.set(item.itemId);

    this.cartService.removeItem(item.itemId).subscribe({
      next: cart => {
        this.cart.set(cart);
        this.removingItemId.set(null);
        this.toastService.info(`${item.name} removed from your cart.`);
      },
      error: (error: ApiError) => {
        this.removingItemId.set(null);
        this.toastService.error(error.message);
      },
    });
  }

  /** Two-step clear-cart button: arm, then confirm within 3 seconds. */
  clearCart(): void {
    if (!this.clearArmed()) {
      this.clearArmed.set(true);
      this.clearTimer = timer(3000).subscribe(() => this.clearArmed.set(false));
      return;
    }

    this.disarmClear();
    this.clearing.set(true);
    this.cartService.clearCart().subscribe({
      next: cart => {
        this.cart.set(cart);
        this.clearing.set(false);
        this.toastService.info('Your cart has been emptied.');
      },
      error: (error: ApiError) => {
        this.clearing.set(false);
        this.toastService.error(error.message);
      },
    });
  }

  private disarmClear(): void {
    this.clearTimer?.unsubscribe();
    this.clearArmed.set(false);
  }

  goToCheckout(): void {
    this.router.navigate(['/checkout']);
  }
}
