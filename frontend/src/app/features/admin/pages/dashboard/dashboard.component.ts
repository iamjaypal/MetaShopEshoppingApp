import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { AdminService } from '../../../../core/services/admin.service';
import { ApiError } from '../../../../core/models/api-error';
import { Inventory } from '../../../../core/models/admin.model';
import { Order } from '../../../../core/models/order.model';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';

/**
 * Admin dashboard: catalogue/account sizes, the payment ledger volume, the
 * recent orders and what is running low on stock.
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, RouterLink, SkeletonComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {

  private readonly adminService = inject(AdminService);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly productCount = signal(0);
  readonly orderCount = signal(0);
  readonly paymentCount = signal(0);
  readonly userCount = signal(0);

  readonly recentOrders = signal<Order[]>([]);
  readonly lowStock = signal<Inventory[]>([]);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);

    // Five parallel list calls; each stat comes from its page total. A failed
    // call leaves that stat at zero instead of killing the whole dashboard.
    let pending = 5;
    const settle = () => {
      pending--;
      if (pending === 0) {
        this.loading.set(false);
      }
    };

    this.adminService.listProducts(0, 1).subscribe({
      next: page => {
        this.productCount.set(page.totalElements);
        settle();
      },
      error: settle,
    });

    this.adminService.listOrders(null, 0, 5).subscribe({
      next: page => {
        this.orderCount.set(page.totalElements);
        this.recentOrders.set(page.content);
        settle();
      },
      error: settle,
    });

    this.adminService.listPayments(0, 1).subscribe({
      next: page => {
        this.paymentCount.set(page.totalElements);
        settle();
      },
      error: settle,
    });

    this.adminService.listUsers(0, 1).subscribe({
      next: page => {
        this.userCount.set(page.totalElements);
        settle();
      },
      error: settle,
    });

    this.adminService.listLowStock(5, 0, 6).subscribe({
      next: page => {
        this.lowStock.set(page.content);
        settle();
      },
      error: settle,
    });
  }
}
