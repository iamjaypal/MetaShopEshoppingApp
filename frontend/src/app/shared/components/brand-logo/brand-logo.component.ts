import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-brand-logo',
  standalone: true,
  templateUrl: './brand-logo.component.html',
  styleUrl: './brand-logo.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrandLogoComponent {

  /** Pixel size of the mark (the wordmark scales with it). */
  readonly size = input(38);

  /** Show the "MetaShop" wordmark next to the mark. */
  readonly showWordmark = input(true);

  /** Light wordmark for dark panels. */
  readonly light = input(false);

  readonly markSize = computed(() => `${this.size()}px`);

  readonly fontSize = computed(() => `${Math.round(this.size() * 0.52)}px`);
}
