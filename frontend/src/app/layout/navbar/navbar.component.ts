import { ChangeDetectionStrategy, Component, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { CartService } from '../../core/services/cart.service';
import { BrandLogoComponent } from '../../shared/components/brand-logo/brand-logo.component';

/**
 * The app-wide top bar: brand, primary navigation, cart badge and the user
 * area. Sticky glass bar that gains a shadow once the page scrolls; collapses
 * into a slide-down menu on small screens.
 */
@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, BrandLogoComponent],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavbarComponent {

  private readonly auth = inject(AuthService);
  private readonly cart = inject(CartService);
  private readonly router = inject(Router);

  readonly user = this.auth.currentUser;
  readonly isAdmin = this.auth.isAdmin;
  readonly cartCount = this.cart.count;

  /** Solid + shadow once the page is scrolled past the top. */
  readonly scrolled = signal(false);

  readonly menuOpen = signal(false);
  readonly userMenuOpen = signal(false);

  @HostListener('window:scroll')
  onScroll(): void {
    this.scrolled.set(window.scrollY > 12);
  }

  /** Close every menu when clicking anywhere outside them. */
  @HostListener('document:click')
  onDocumentClick(): void {
    this.userMenuOpen.set(false);
  }

  initials(): string {
    const user = this.user();
    return user
      ? (user.firstName.charAt(0) + user.lastName.charAt(0)).toUpperCase()
      : '?';
  }

  toggleUserMenu(event: MouseEvent): void {
    event.stopPropagation(); // keep it alive against the document closer
    this.userMenuOpen.update(open => !open);
  }

  closeMenus(): void {
    this.menuOpen.set(false);
    this.userMenuOpen.set(false);
  }

  signOut(): void {
    this.closeMenus();
    this.auth.logout();
  }

  /** Public nav links; the catalogue/orders pages exist as stubs for now. */
  readonly links = [
    { label: 'Home', route: '/home' },
    { label: 'Shop', route: '/products' },
    { label: 'Orders', route: '/orders' },
  ];
}
