import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * A shimmering placeholder block shown while data loads. Size and rounding
 * are set by the caller; the shimmer animation is built in.
 */
@Component({
  selector: 'app-skeleton',
  standalone: true,
  templateUrl: './skeleton.component.html',
  styleUrl: './skeleton.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkeletonComponent {

  readonly width = input('100%');

  readonly height = input('16px');

  /** CSS border-radius, e.g. `12px` or `50%` for circles. */
  readonly radius = input('10px');
}
