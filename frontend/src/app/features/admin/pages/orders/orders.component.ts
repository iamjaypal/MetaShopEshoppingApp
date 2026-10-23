import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';

import { AdminService } from '../../../../core/services/admin.service';
import { ApiError } from '../../../../core/models/api-error';
import { ORDER_STATUSES } from '../../../../core/models/admin.model';
import { Order, OrderStatus } from '../../../../core/models/order.model';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';
import { ToastService } from '../../../../core/services/toast.service';

/** Admin order management: filter by status, move orders through the flow. */
@Component({
  selector: 'app-admin-orders',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, SkeletonComponent],
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminOrdersPageComponent {

  private readonly adminService = inject(AdminService);
  private readonly toast = inject(ToastService);

  readonly statusOptions = [null, ...ORDER_STATUSES];

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly orders = signal<Order[]>([]);
  readonly filter = signal<OrderStatus | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  /** The order whose status select is mid-flight. */
  readonly updatingId = signal<number | null>(null);

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.loadError.set(null);

    this.adminService.listOrders(this.filter(), page, 10).subscribe({
      next: pageData => {
        this.orders.set(pageData.content);
        this.page.set(pageData.page ?? page);
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

  setFilter(status: OrderStatus | null): void {
    this.filter.set(status);
    this.load(0);
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages() || page === this.page()) {
      return;
    }
    this.load(page);
  }

  /** Applies a new status from the row's select. */
  changeStatus(order: Order, status: string): void {
    if (status === order.status || this.updatingId() !== null) {
      return;
    }

    this.updatingId.set(order.id);
    this.adminService.updateOrderStatus(order.id, status as OrderStatus).subscribe({
      next: updated => {
        this.updatingId.set(null);
        this.orders.update(list => list.map(item =>
          item.id === updated.id ? updated : item));
        this.toast.success(`Order #${order.id} → ${updated.status}.`);
      },
      error: (error: ApiError) => {
        this.updatingId.set(null);
        this.toast.error(error.message);
        this.load(this.page()); // re-sync the select with the server truth
      },
    });
  }
}
