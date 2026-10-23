import {
  ChangeDetectionStrategy, Component, OnDestroy, computed, inject, signal,
} from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import {
  AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators,
} from '@angular/forms';
import { Subscription, timer } from 'rxjs';

import { AdminService, AdminBrand, AdminCategory } from '../../../../core/services/admin.service';
import { ApiError } from '../../../../core/models/api-error';
import { ProductDetail, ProductSummary } from '../../../../core/models/product.model';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';
import { ToastService } from '../../../../core/services/toast.service';

/** The admin product table + editor drawer (create, edit, images, status). */
@Component({
  selector: 'app-admin-products',
  standalone: true,
  imports: [CurrencyPipe, ReactiveFormsModule, SkeletonComponent],
  templateUrl: './products.component.html',
  styleUrl: './products.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminProductsPageComponent implements OnDestroy {

  private readonly adminService = inject(AdminService);
  private readonly toast = inject(ToastService);

  // -------------------------------------------------------------- the list
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly products = signal<ProductSummary[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  readonly searchInput = signal('');
  readonly activeFilter = signal<boolean | null>(null);

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  // ----------------------------------------------------------- row actions
  readonly togglingId = signal<number | null>(null);
  readonly deletingId = signal<number | null>(null);
  private deleteTimer: Subscription | null = null;

  // -------------------------------------------------------------- the form
  readonly editorOpen = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly attempted = signal(false);

  readonly categories = signal<AdminCategory[]>([]);
  readonly brands = signal<AdminBrand[]>([]);

  readonly productForm = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(140)],
    }),
    sku: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(40)],
    }),
    shortDescription: new FormControl('', {
      nonNullable: true, validators: [Validators.maxLength(250)],
    }),
    description: new FormControl('', {
      nonNullable: true, validators: [Validators.maxLength(4000)],
    }),
    price: new FormControl<number | null>(null, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0.01)],
    }),
    mrp: new FormControl<number | null>(null, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0.01)],
    }),
    categoryId: new FormControl<number | null>(null, {
      nonNullable: true, validators: [Validators.required],
    }),
    brandId: new FormControl<number | null>(null, {
      nonNullable: true, validators: [Validators.required],
    }),
    active: new FormControl(true, { nonNullable: true }),
  });

  /** Images of the product being edited (empty for a new product). */
  readonly editorImages = signal<ProductDetail['images']>([]);
  readonly uploading = signal(false);
  readonly deletingImageId = signal<number | null>(null);

  readonly pageWindow = computed(() => this.page() + 1);

  constructor() {
    this.load(0);
    this.loadTaxonomy();
  }

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.deleteTimer?.unsubscribe();
  }

  // ------------------------------------------------------------- the list

  load(page: number): void {
    this.loading.set(true);
    this.loadError.set(null);

    this.adminService.listProducts(page, 10, this.searchInput(), this.activeFilter())
      .subscribe({
        next: pageData => {
          this.products.set(pageData.content);
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

  loadTaxonomy(): void {
    this.adminService.listCategories().subscribe({
      next: list => this.categories.set(list),
      error: () => {},
    });
    this.adminService.listBrands().subscribe({
      next: list => this.brands.set(list),
      error: () => {},
    });
  }

  onSearchInput(value: string): void {
    this.searchInput.set(value);
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => this.load(0), 400);
  }

  setFilter(value: boolean | null): void {
    this.activeFilter.set(value);
    this.load(0);
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages() || page === this.page()) {
      return;
    }
    this.load(page);
  }

  // ------------------------------------------------------------ row actions

  toggleActive(product: ProductSummary): void {
    if (this.togglingId() !== null) {
      return;
    }
    this.togglingId.set(product.id);

    this.adminService.setActive(product.id, !product.active).subscribe({
      next: () => {
        this.togglingId.set(null);
        this.products.update(list => list.map(item =>
          item.id === product.id ? { ...item, active: !item.active } : item));
        this.toast.success(`${product.name} is now ${product.active ? 'hidden' : 'live'}.`);
      },
      error: (error: ApiError) => {
        this.togglingId.set(null);
        this.toast.error(error.message);
      },
    });
  }

  /** Two-step delete: arm, then confirm within 3 seconds. */
  remove(product: ProductSummary): void {
    if (this.deletingId() !== product.id) {
      this.deleteTimer?.unsubscribe();
      this.deletingId.set(product.id);
      this.deleteTimer = timer(3000).subscribe(() => this.deletingId.set(null));
      return;
    }

    this.deleteTimer?.unsubscribe();
    this.deletingId.set(null);
    this.adminService.deleteProduct(product.id).subscribe({
      next: () => {
        this.products.update(list => list.filter(item => item.id !== product.id));
        this.totalElements.update(n => Math.max(0, n - 1));
        this.toast.info(`${product.name} deleted.`);
      },
      error: (error: ApiError) => this.toast.error(error.message),
    });
  }

  // ---------------------------------------------------------------- editor

  startCreate(): void {
    this.editingId.set(null);
    this.editorImages.set([]);
    this.productForm.reset({
      name: '', sku: '', shortDescription: '', description: '',
      price: null, mrp: null, categoryId: null, brandId: null, active: true,
    });
    this.attempted.set(false);
    this.editorOpen.set(true);
  }

  startEdit(product: ProductSummary): void {
    this.editingId.set(product.id);
    this.editorOpen.set(true);
    this.attempted.set(false);

    // The summary has everything except the long description + gallery.
    this.adminService.getProduct(product.id).subscribe({
      next: detail => {
        this.productForm.setValue({
          name: detail.name,
          sku: detail.sku,
          shortDescription: detail.shortDescription ?? '',
          description: detail.description ?? '',
          price: detail.price,
          mrp: detail.mrp,
          categoryId: detail.categoryId,
          brandId: detail.brandId,
          active: detail.active,
        });
        this.editorImages.set(detail.images ?? []);
      },
      error: (error: ApiError) => {
        this.editorOpen.set(false);
        this.toast.error(error.message);
      },
    });
  }

  closeEditor(): void {
    this.editorOpen.set(false);
    this.editingId.set(null);
  }

  hasError(control: AbstractControl): boolean {
    return control.invalid && (control.touched || this.attempted());
  }

  saveProduct(): void {
    if (this.productForm.invalid || this.saving()) {
      this.attempted.set(true);
      return;
    }

    const request = {
      ...this.productForm.getRawValue(),
      price: Number(this.productForm.controls.price.value),
      mrp: Number(this.productForm.controls.mrp.value),
      categoryId: Number(this.productForm.controls.categoryId.value),
      brandId: Number(this.productForm.controls.brandId.value),
    };

    const editingId = this.editingId();
    this.saving.set(true);

    const call = editingId === null
      ? this.adminService.createProduct(request)
      : this.adminService.updateProduct(editingId, request);

    call.subscribe({
      next: saved => {
        this.saving.set(false);
        this.closeEditor();
        this.toast.success(editingId === null
          ? `${saved.name} created.`
          : `${saved.name} updated.`);
        this.load(this.page());
      },
      error: (error: ApiError) => {
        this.saving.set(false);
        this.toast.error(error.message);
      },
    });
  }

  // ---------------------------------------------------------------- images

  uploadImage(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const productId = this.editingId();
    if (!file || !productId || this.uploading()) {
      return;
    }

    this.uploading.set(true);
    this.adminService.uploadImage(productId, file).subscribe({
      next: () => {
        this.uploading.set(false);
        input.value = '';
        this.toast.success('Image uploaded.');
        this.refreshImages(productId);
      },
      error: (error: ApiError) => {
        this.uploading.set(false);
        this.toast.error(error.message);
      },
    });
  }

  removeImage(imageId: number): void {
    const productId = this.editingId();
    if (!productId || this.deletingImageId() !== null) {
      return;
    }
    this.deletingImageId.set(imageId);

    this.adminService.deleteImage(productId, imageId).subscribe({
      next: () => {
        this.deletingImageId.set(null);
        this.toast.info('Image removed.');
        this.refreshImages(productId);
      },
      error: (error: ApiError) => {
        this.deletingImageId.set(null);
        this.toast.error(error.message);
      },
    });
  }

  private refreshImages(productId: number): void {
    this.adminService.getProduct(productId).subscribe({
      next: detail => this.editorImages.set(detail.images ?? []),
      error: () => {},
    });
  }
}
