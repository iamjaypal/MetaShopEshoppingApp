import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ProductSummary } from '../../../core/models/product.model';

/**
 * One product tile, used by every grid in the app (home now, listing and
 * wishlists later). Shows the image, brand, name, rating and the price with
 * the MRP discount. The whole card links to the product detail page.
 */
@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './product-card.component.html',
  styleUrl: './product-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductCardComponent {

  readonly product = input.required<ProductSummary>();

  /** image load failures fall back to a gradient tile with the brand initial */
  private readonly imageFailed = signal(false);

  readonly showImage = computed(() => !this.imageFailed());

  /** Discount percent vs MRP, e.g. 20 → "-20%". */
  readonly discountPercent = computed(() => {
    const { price, mrp } = this.product();
    if (!mrp || mrp <= price) {
      return 0;
    }
    return Math.round(((mrp - price) / mrp) * 100);
  });

  /** Whole stars for the rating row (0–5). */
  readonly filledStars = computed(() =>
    Math.min(5, Math.round(this.product().averageRating ?? 0)));

  onImageError(): void {
    this.imageFailed.set(true);
  }
}
