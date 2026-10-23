import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

/**
 * Honest placeholder for pages that arrive in a later phase (product listing,
 * product detail, cart, orders). Reads the route's `data.title` so the copy
 * says what is coming.
 */
@Component({
  selector: 'app-coming-soon',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './coming-soon.component.html',
  styleUrl: './coming-soon.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComingSoonComponent {

  private readonly route = inject(ActivatedRoute);

  /** Which feature is on its way — set as `data.title` on the route. */
  readonly title = computed(() => this.route.snapshot.data['title'] ?? 'This page');

  readonly phase = computed(() => this.route.snapshot.data['phase'] ?? 'F3');
}
