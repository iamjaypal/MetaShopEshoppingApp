import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';

import { AdminService } from '../../../../core/services/admin.service';
import { ApiError } from '../../../../core/models/api-error';
import { AdminUser, AdminUserPage } from '../../../../core/models/admin.model';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';
import { ToastService } from '../../../../core/services/toast.service';

/** Admin user management: who is registered, who can sign in. */
@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [DatePipe, SkeletonComponent],
  templateUrl: './users.component.html',
  styleUrl: './users.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminUsersPageComponent {

  private readonly adminService = inject(AdminService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly users = signal<AdminUser[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  /** The user whose enable/disable call is in flight. */
  readonly updatingId = signal<number | null>(null);

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.loadError.set(null);

    this.adminService.listUsers(page, 10).subscribe({
      next: pageData => {
        this.users.set(pageData.content);
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

  toggleEnabled(user: AdminUser): void {
    if (this.updatingId() !== null) {
      return;
    }
    this.updatingId.set(user.id);

    this.adminService.setUserEnabled(user.id, !user.enabled).subscribe({
      next: updated => {
        this.updatingId.set(null);
        this.users.update(list => list.map(item =>
          item.id === updated.id ? updated : item));
        this.toast.success(updated.enabled
          ? `${updated.email} can sign in again.`
          : `${updated.email} is now blocked from signing in.`);
      },
      error: (error: ApiError) => {
        this.updatingId.set(null);
        this.toast.error(error.message);
      },
    });
  }
}
