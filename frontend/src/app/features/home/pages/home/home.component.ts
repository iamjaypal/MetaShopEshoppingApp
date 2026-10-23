import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable, catchError, finalize, map, of } from 'rxjs';

import { ProductService } from '../../../../core/services/product.service';
import { ApiError } from '../../../../core/models/api-error';
import { Category, ProductSummary } from '../../../../core/models/product.model';
import { ProductCardComponent } from '../../../../shared/components/product-card/product-card.component';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';

/**
 * The storefront home page: hero with floating live products, the category
 * strip, fresh arrivals from product-service, the trust band and a CTA.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, CurrencyPipe, ProductCardComponent, SkeletonComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {

  private readonly productService = inject(ProductService);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly categories = signal<Category[]>([]);
  readonly products = signal<ProductSummary[]>([]);

  /** The first three products float through the hero collage. */
  readonly heroProducts = signal<ProductSummary[]>([]);

  ngOnInit(): void {
    this.loadCatalogue();
  }

  retry(): void {
    this.loadCatalogue();
  }

  private loadCatalogue(): void {
    this.loading.set(true);
    this.loadError.set(null);

    // Products drive the grid AND the hero collage — a failure here is fatal
    // for the page and shows the retry banner.
    this.productService.getProducts({ page: 0, size: 8 })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: page => {
          this.products.set(page.content);
          this.heroProducts.set(page.content.slice(0, 3));
        },
        error: (error: ApiError) => this.loadError.set(error.message),
      });

    // Categories are a side strip — a failure there just hides the strip.
    this.productService.getCategories()
      .pipe(catchError(() => of(null)))
      .subscribe(categories => {
        if (categories) {
          this.categories.set(categories);
        }
      });
  }

  /** Icon name for a category tile, from its slug. */
  categoryIcon(slug: string): string {
    if (slug.includes('electronic')) return 'cpu';
    if (slug.includes('fashion')) return 'shirt';
    if (slug.includes('home') || slug.includes('kitchen')) return 'home';
    if (slug.includes('sport')) return 'trophy';
    return 'tag';
  }
}
