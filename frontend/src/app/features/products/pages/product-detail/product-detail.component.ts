import {
  ChangeDetectionStrategy, Component, OnDestroy, computed, inject, signal,
} from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { ApiError } from '../../../../core/models/api-error';
import {
  Category, InventoryView, ProductDetail, ProductSummary,
} from '../../../../core/models/product.model';
import { AuthService } from '../../../../core/services/auth.service';
import { CartService } from '../../../../core/services/cart.service';
import { InventoryService } from '../../../../core/services/inventory.service';
import { ProductService } from '../../../../core/services/product.service';
import { ProductCardComponent } from '../../../../shared/components/product-card/product-card.component';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';
import { ToastService } from '../../../../core/services/toast.service';

/**
 * A single product: gallery, price, stock, specs and add-to-cart. Related
 * products from the same category close the page.
 */
@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [CurrencyPipe, RouterLink, ProductCardComponent, SkeletonComponent],
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductDetailComponent implements OnDestroy {

  private readonly productService = inject(ProductService);
  private readonly inventoryService = inject(InventoryService);
  private readonly cartService = inject(CartService);
  private readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly titleService = inject(Title);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly product = signal<ProductDetail | null>(null);

  readonly activeImage = signal(0);
  readonly quantity = signal(1);
  readonly stock = signal<InventoryView | null>(null);
  readonly stockChecked = signal(false);
  readonly related = signal<ProductSummary[]>([]);
  readonly relatedLoading = signal(false);

  readonly adding = signal(false);
  readonly justAdded = signal(false);

  /** Resolved slug of the product's category, for the breadcrumb link. */
  readonly categorySlug = signal<string | null>(null);

  /** Gallery ordered primary-first, then by sort order. */
  readonly gallery = computed<ProductDetail['images']>(() => {
    const product = this.product();
    if (!product) {
      return [];
    }
    return [...product.images]
      .sort((a, b) => Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder);
  });

  /** Discount vs MRP, e.g. 20 (percent); 0 hides the badge. */
  readonly discountPercent = computed(() => {
    const product = this.product();
    if (!product || product.mrp <= product.price) {
      return 0;
    }
    return Math.round(((product.mrp - product.price) / product.mrp) * 100);
  });

  /** Stars 1..5 that light up (rounded to the nearest half → floor). */
  readonly filledStars = computed(() => Math.round(this.product()?.averageRating ?? 0));

  /** Quantity picker ceiling: what inventory allows, capped at 10. */
  readonly maxQuantity = computed(() =>
    Math.min(this.stock()?.availableQuantity ?? 10, 10));

  /** One of `in` | `low` | `out` | `unknown` — drives the stock chip. */
  readonly stockState = computed<'in' | 'low' | 'out' | 'unknown' | null>(() => {
    if (!this.stockChecked()) {
      return null;
    }
    const stock = this.stock();
    if (!stock || !stock.inStock || stock.availableQuantity <= 0) {
      return 'out';
    }
    return stock.availableQuantity <= 5 ? 'low' : 'in';
  });

  private paramSub: Subscription;

  constructor() {
    this.paramSub = this.route.paramMap.subscribe(params => {
      const id = Number(params.get('id'));
      if (Number.isInteger(id) && id > 0) {
        this.load(id);
      } else {
        this.loadError.set('That product link looks broken.');
        this.loading.set(false);
      }
    });
  }

  ngOnDestroy(): void {
    this.paramSub.unsubscribe();
  }

  // ------------------------------------------------------------- gallery

  selectImage(index: number): void {
    this.activeImage.set(index);
  }

  // ----------------------------------------------------------- quantity

  increaseQuantity(): void {
    this.quantity.set(Math.min(this.quantity() + 1, this.maxQuantity()));
  }

  decreaseQuantity(): void {
    this.quantity.set(Math.max(this.quantity() - 1, 1));
  }

  // -------------------------------------------------------- add to cart

  addToCart(): void {
    const product = this.product();
    if (!product || this.adding()) {
      return;
    }

    if (!this.authService.isAuthenticated()) {
      this.toastService.info('Sign in to start a cart.');
      this.router.navigate(['/login'], {
        queryParams: { returnUrl: this.router.url },
      });
      return;
    }

    this.adding.set(true);
    this.cartService.addItem(product.id, this.quantity()).subscribe({
      next: () => {
        this.adding.set(false);
        this.toastService.success(`${product.name} added to your cart.`);
        this.flashAdded();
      },
      error: (error: ApiError) => {
        this.adding.set(false);
        this.toastService.error(error.message);
      },
    });
  }

  /** Briefly flips the button into a confirmation, then back. */
  private flashAdded(): void {
    this.justAdded.set(true);
    setTimeout(() => this.justAdded.set(false), 1800);
  }

  // ------------------------------------------------------------- loading

  private load(id: number): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.product.set(null);
    this.activeImage.set(0);
    this.quantity.set(1);
    this.stock.set(null);
    this.stockChecked.set(false);
    this.related.set([]);
    window.scrollTo({ top: 0 });

    this.productService.getProduct(id).subscribe({
      next: product => {
        this.product.set(product);
        this.loading.set(false);
        this.titleService.setTitle(`${product.name} · MetaShop`);

        this.loadStock(id);
        this.loadRelated(product);
        this.loadCategory(product.categoryId);
      },
      error: (error: ApiError) => {
        this.loadError.set(error.status === 404
          ? 'We couldn\u2019t find that product — it may have been removed.'
          : error.message);
        this.loading.set(false);
      },
    });
  }

  private loadStock(productId: number): void {
    this.inventoryService.getStock(productId).subscribe({
      next: stock => {
        this.stock.set(stock);
        this.stockChecked.set(true);
        this.quantity.set(Math.min(this.quantity(), this.maxQuantity() || 1));
      },
      error: () => this.stockChecked.set(true), // chip falls back to "unknown"
    });
  }

  private loadRelated(product: ProductDetail): void {
    this.relatedLoading.set(true);
    this.productService.getProducts({ page: 0, size: 24 }).subscribe({
      next: pageData => {
        this.related.set(pageData.content
          .filter(item => item.categoryId === product.categoryId && item.id !== product.id)
          .slice(0, 4));
        this.relatedLoading.set(false);
      },
      error: () => this.relatedLoading.set(false),
    });
  }

  /** Resolves the category slug for the breadcrumb link (name → slug). */
  private loadCategory(categoryId: number): void {
    this.productService.getCategories().subscribe(categories => {
      const match = ProductDetailComponent.findCategory(categories, categoryId);
      if (match) {
        this.categorySlug.set(match.slug);
      }
    });
  }

  private static findCategory(nodes: Category[], id: number): Category | null {
    for (const node of nodes) {
      if (node.id === id) {
        return node;
      }
      const child = ProductDetailComponent.findCategory(node.children ?? [], id);
      if (child) {
        return child;
      }
    }
    return null;
  }
}
