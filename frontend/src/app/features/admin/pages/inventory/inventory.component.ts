import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { AdminService } from '../../../../core/services/admin.service';
import { ApiError } from '../../../../core/models/api-error';
import { Inventory } from '../../../../core/models/admin.model';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';
import { ToastService } from '../../../../core/services/toast.service';

/**
 * Inventory management: the low-stock watchlist plus a stock editor that can
 * set an absolute quantity for any product (creating the row if it is new).
 */
@Component({
  selector: 'app-admin-inventory',
  standalone: true,
  imports: [SkeletonComponent],
  templateUrl: './inventory.component.html',
  styleUrl: './inventory.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminInventoryPageComponent {

  private readonly adminService = inject(AdminService);
  private readonly toast = inject(ToastService);

  // ------------------------------------------------------------- watchlist
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly lowStock = signal<Inventory[]>([]);

  // ---------------------------------------------------------------- editor
  readonly productId = signal('');
  readonly editing = signal<Inventory | null>(null);
  readonly editingNew = signal(false);
  readonly editingSku = signal('');
  readonly quantity = signal<number | null>(null);
  readonly threshold = signal(3);
  readonly saving = signal(false);
  readonly lookupLoading = signal(false);

  constructor() {
    this.loadLowStock();
  }

  loadLowStock(): void {
    this.loading.set(true);
    this.loadError.set(null);

    this.adminService.listLowStock(5, 0, 20).subscribe({
      next: page => {
        this.lowStock.set(page.content);
        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.loadError.set(error.message);
        this.loading.set(false);
      },
    });
  }

  // ---------------------------------------------------------------- editor

  loadStock(): void {
    const id = Number(this.productId());
    if (!Number.isInteger(id) || id <= 0) {
      this.toast.error('Enter a numeric product id first.');
      return;
    }

    this.lookupLoading.set(true);
    this.adminService.getStock(id).subscribe({
      next: stock => {
        this.lookupLoading.set(false);
        this.editing.set(stock);
        this.editingNew.set(false);
        this.editingSku.set(stock.sku);
        this.quantity.set(stock.availableQuantity);
        this.threshold.set(stock.lowStockThreshold);
      },
      error: (error: ApiError) => {
        this.lookupLoading.set(false);
        if (error.status === 404) {
          // No row yet — offer to create one.
          this.editing.set(null);
          this.editingNew.set(true);
          this.editingSku.set('');
          this.quantity.set(0);
          this.threshold.set(3);
        } else {
          this.toast.error(error.message);
        }
      },
    });
  }

  saveStock(): void {
    const id = Number(this.productId());
    const quantity = this.quantity();
    const threshold = this.threshold();
    if (!Number.isInteger(id) || quantity === null || !this.editingNew() && !this.editing()) {
      return;
    }
    if (quantity < 0 || threshold < 0) {
      this.toast.error('Quantity and threshold must be zero or more.');
      return;
    }

    this.saving.set(true);
    const sku = this.editingSku();

    const call = this.editingNew()
      ? this.adminService.createStock(id, sku || `SKU-${id}`, quantity, threshold)
      : this.adminService.setStock(id, quantity, threshold);

    call.subscribe({
      next: stock => {
        this.saving.set(false);
        this.editing.set(stock);
        this.editingNew.set(false);
        this.editingSku.set(stock.sku);
        this.quantity.set(stock.availableQuantity);
        this.threshold.set(stock.lowStockThreshold);
        this.toast.success(`Stock saved: ${stock.availableQuantity} available for ${stock.sku}.`);
        this.loadLowStock();
      },
      error: (error: ApiError) => {
        this.saving.set(false);
        this.toast.error(error.message);
      },
    });
  }

  /** Number inputs answer with strings — coerce to a positive integer or null. */
  setQuantity(value: string): void {
    if (value === '') {
      this.quantity.set(null);
      return;
    }
    const parsed = Number(value);
    this.quantity.set(Number.isFinite(parsed) && parsed >= 0 ? parsed : null);
  }

  setThreshold(value: string): void {
    const parsed = Number(value);
    this.threshold.set(Number.isFinite(parsed) && parsed >= 0 ? parsed : 0);
  }

  editRow(stock: Inventory): void {
    this.productId.set(String(stock.productId));
    this.editing.set(stock);
    this.editingNew.set(false);
    this.editingSku.set(stock.sku);
    this.quantity.set(stock.availableQuantity);
    this.threshold.set(stock.lowStockThreshold);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
