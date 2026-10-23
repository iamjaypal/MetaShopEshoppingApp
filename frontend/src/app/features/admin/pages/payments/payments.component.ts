import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';

import { AdminService } from '../../../../core/services/admin.service';
import { ApiError } from '../../../../core/models/api-error';
import { AdminPaymentPage } from '../../../../core/services/admin.service';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';

/** The payment ledger: every attempt, captured or declined. */
@Component({
  selector: 'app-admin-payments',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, SkeletonComponent],
  templateUrl: './payments.component.html',
  styleUrl: './payments.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPaymentsPageComponent {

  private readonly adminService = inject(AdminService);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly payments = signal<AdminPaymentPage['content']>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.loadError.set(null);

    this.adminService.listPayments(page, 10).subscribe({
      next: pageData => {
        this.payments.set(pageData.content);
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

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages() || page === this.page()) {
      return;
    }
    this.load(page);
  }
}
