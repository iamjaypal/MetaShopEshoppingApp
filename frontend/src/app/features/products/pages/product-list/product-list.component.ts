import {
  ChangeDetectionStrategy, Component, OnDestroy, inject, signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subscription, catchError, of } from 'rxjs';

import { ProductService } from '../../../../core/services/product.service';
import { ApiError } from '../../../../core/models/api-error';
import {
  Brand, Category, ProductSummary,
} from '../../../../core/models/product.model';
import { ProductCardComponent } from '../../../../shared/components/product-card/product-card.component';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';

/**
 * The product listing: search, category/brand/price filters, sorting and
 * pagination — all reflected in the URL's query params so lists are
 * shareable and the browser back button behaves.
 */
@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [FormsModule, ProductCardComponent, SkeletonComponent],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductListComponent implements OnDestroy {

  private readonly productService = inject(ProductService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Sort options (the backend whitelists these). */
  readonly sortOptions = [
    { value: 'createdAt,desc', label: 'Newest first' },
    { value: 'price,asc', label: 'Price: low to high' },
    { value: 'price,desc', label: 'Price: high to low' },
    { value: 'averageRating,desc', label: 'Top rated' },
  ];

  readonly categories = signal<Category[]>([]);
  readonly brands = signal<Brand[]>([]);
  readonly products = signal<ProductSummary[]>([]);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  // ---- current filter state (mirrors the query params) ----
  readonly q = signal('');
  readonly sort = signal('createdAt,desc');
  readonly category = signal('');
  readonly brand = signal('');
  readonly minPrice = signal<number | null>(null);
  readonly maxPrice = signal<number | null>(null);

  /** Live value of the search box; the URL updates after a short debounce. */
  readonly searchInput = signal('');

  readonly filtersOpen = signal(false);

  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private querySub: Subscription;

  /** Page numbers for the pagination strip, e.g. 1 … 4 [5] 6 … 20. */
  pages(): number[] {
    const current = this.page();
    const last = this.totalPages();
    const window = 1;
    const numbers = new Set<number>([0, last - 1, current, current - 1, current + 1]);

    return [...numbers]
      .filter(p => p >= 0 && p < last)
      .sort((a, b) => a - b);
  }

  constructor() {
    // Load the filter metadata once.
    this.productService.getCategories()
      .pipe(catchErrorToNull())
      .subscribe(categories => categories && this.categories.set(categories));
    this.productService.getBrands()
      .pipe(catchErrorToNull())
      .subscribe(brands => brands && this.brands.set(brands));

    // Every change of the query params re-runs the listing.
    this.querySub = this.route.queryParamMap.subscribe(params => {
      this.q.set(params.get('q') ?? '');
      this.searchInput.set(this.q());
      this.sort.set(params.get('sort') ?? 'createdAt,desc');
      this.category.set(params.get('category') ?? '');
      this.brand.set(params.get('brand') ?? '');
      this.minPrice.set(params.get('minPrice') ? Number(params.get('minPrice')) : null);
      this.maxPrice.set(params.get('maxPrice') ? Number(params.get('maxPrice')) : null);
      this.page.set(Number(params.get('page') ?? 0));

      this.load();
    });
  }

  ngOnDestroy(): void {
    this.querySub.unsubscribe();
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
  }

  // ------------------------------------------------------------- handlers

  onSearchInput(value: string): void {
    this.searchInput.set(value);

    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(
      () => this.setParam({ q: value || null, page: null }), 400);
  }

  setSort(sort: string): void {
    this.setParam({ sort: sort === 'createdAt,desc' ? null : sort, page: null });
  }

  setCategory(slug: string): void {
    this.setParam({ category: slug || null, page: null });
  }

  setBrand(name: string): void {
    this.setParam({ brand: name || null, page: null });
  }

  applyPrice(): void {
    this.setParam({
      minPrice: this.minPrice(),
      maxPrice: this.maxPrice(),
      page: null,
    });
  }

  /** Price inputs yield `''` while empty — store that as `null` instead. */
  setMinPrice(value: string | number): void {
    this.minPrice.set(ProductListComponent.toPrice(value));
  }

  setMaxPrice(value: string | number): void {
    this.maxPrice.set(ProductListComponent.toPrice(value));
  }

  private static toPrice(value: string | number | null): number | null {
    if (value === null || value === '') {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
  }

  clearPrice(): void {
    this.minPrice.set(null);
    this.maxPrice.set(null);
    this.setParam({ minPrice: null, maxPrice: null, page: null });
  }

  goToPage(page: number): void {
    if (page === this.page()) {
      return;
    }
    this.setParam({ page: page > 0 ? page : null });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  /** Human name of the active category slug (for the header line). */
  categoryLabel(): string {
    const slug = this.category();
    const match = this.categories().find(cat => cat.slug === slug);
    return match ? match.name : slug;
  }

  hasActiveFilters(): boolean {
    return Boolean(this.q() || this.category() || this.brand()
      || this.minPrice() !== null || this.maxPrice() !== null);
  }

  clearAllFilters(): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  toggleFilters(): void {
    this.filtersOpen.update(open => !open);
  }

  /** Merges one filter change into the URL; `null` removes the param. */
  private setParam(change: Record<string, string | number | null | undefined>): void {
    const queryParams: Record<string, string | number> = {};

    const merged = { ...this.currentParams(), ...change };
    for (const [key, value] of Object.entries(merged)) {
      if (value !== null && value !== undefined && value !== '') {
        queryParams[key] = value as string | number;
      }
    }

    this.router.navigate([], { relativeTo: this.route, queryParams });
  }

  private currentParams(): Record<string, string | number | null> {
    return {
      q: this.q() || null,
      sort: this.sort() === 'createdAt,desc' ? null : this.sort(),
      category: this.category() || null,
      brand: this.brand() || null,
      minPrice: this.minPrice(),
      maxPrice: this.maxPrice(),
      page: this.page() > 0 ? this.page() : null,
    };
  }

  private load(): void {
    this.loading.set(true);
    this.loadError.set(null);

    this.productService.getProducts({
      page: this.page(),
      size: 12,
      sort: this.sort(),
      q: this.q() || undefined,
      category: this.category() || undefined,
      brand: this.brand() || undefined,
      minPrice: this.minPrice() ?? undefined,
      maxPrice: this.maxPrice() ?? undefined,
    }).subscribe({
      next: pageData => {
        this.products.set(pageData.content);
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
}

/** A failed metadata call (categories/brands) just hides that filter group. */
function catchErrorToNull<T>() {
  return (source: Observable<T>): Observable<T | null> =>
    source.pipe(catchError(() => of(null)));
}
