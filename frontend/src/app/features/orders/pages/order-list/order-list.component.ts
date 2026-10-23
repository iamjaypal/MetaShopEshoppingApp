import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Subscription, timer } from 'rxjs';

import { ApiError } from '../../../../core/models/api-error';
import { Order } from '../../../../core/models/order.model';
import { OrderService } from '../../../../core/services/order.service';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';
import { ToastService } from '../../../../core/services/toast.service';

/**
 * The order history: paged cards with items, status chips and the actions
 * that make sense per state — pay an unpaid order, cancel a reserved one.
 */
@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, RouterLink, SkeletonComponent],
  templateUrl: './order-list.component.html',
  styleUrl: './order-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderListPageComponent {

  private readonly orderService = inject(OrderService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly orders = signal<Order[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  /** The order line currently being cancelled on the server. */
  readonly cancellingId = signal<number | null>(null);

  /** Two-step cancel: the order id whose button is armed right now. */
  readonly cancelArmedId = signal<number | null>(null);

  private cancelTimer: Subscription | null = null;

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.loadError.set(null);

    this.orderService.getOrders(page, 10).subscribe({
      next: pageData => {
        this.orders.set(pageData.content);
        this.page.set(pageData.page);
        this.totalPages.set(pageData.totalPages);
        this.totalElements.set(pageData.totalElements);
        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.loadError.set(error.message);
        this.loading.set(false);
      },
    });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages() || page === this.page()) {
      return;
    }
    this.load(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  pay(order: Order): void {
    this.router.navigate(['/orders', order.id, 'pay']);
  }

  /** Two-step cancel button: arm, then confirm within 3 seconds. */
  cancel(order: Order): void {
    if (this.cancelArmedId() !== order.id) {
      this.disarmCancel();
      this.cancelArmedId.set(order.id);
      this.cancelTimer = timer(3000).subscribe(() => this.cancelArmedId.set(null));
      return;
    }

    this.disarmCancel();
    this.cancellingId.set(order.id);

    this.orderService.cancelOrder(order.id).subscribe({
      next: updated => {
        // Replace the card in place so the state chips flip live.
        this.orders.update(list => list.map(item =>
          item.id === updated.id ? updated : item));
        this.cancellingId.set(null);
        this.toastService.info(`Order #${order.id} cancelled — reserved stock released.`);
      },
      error: (error: ApiError) => {
        this.cancellingId.set(null);
        this.toastService.error(error.message);
      },
    });
  }

  private disarmCancel(): void {
    this.cancelTimer?.unsubscribe();
    this.cancelArmedId.set(null);
  }
}
