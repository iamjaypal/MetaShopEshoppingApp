import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';

/**
 * The admin console layout: a fixed sidebar of sections and the routed page.
 * Rendered only behind `adminGuard`.
 */
@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-shell.component.html',
  styleUrl: './admin-shell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminShellComponent {

  private readonly authService = inject(AuthService);

  readonly navOpen = signal(false);

  readonly user = this.authService.currentUser;

  readonly sections = [
    { path: '/admin', label: 'Dashboard', exact: true, icon: 'grid' },
    { path: '/admin/products', label: 'Products', exact: false, icon: 'box' },
    { path: '/admin/inventory', label: 'Inventory', exact: false, icon: 'stack' },
    { path: '/admin/orders', label: 'Orders', exact: false, icon: 'bag' },
    { path: '/admin/payments', label: 'Payments', exact: false, icon: 'card' },
    { path: '/admin/users', label: 'Users', exact: false, icon: 'users' },
  ] as const;

  toggleNav(): void {
    this.navOpen.update(open => !open);
  }

  closeNav(): void {
    this.navOpen.set(false);
  }
}
