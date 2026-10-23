import {
  ChangeDetectionStrategy, Component, effect, inject,
} from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { CartService } from '../../core/services/cart.service';
import { FooterComponent } from '../footer/footer.component';
import { NavbarComponent } from '../navbar/navbar.component';

/**
 * The storefront shell: navbar on top, the routed page in the middle, footer
 * at the bottom. Auth pages (login/register) are rendered outside this shell.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, FooterComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {

  private readonly authService = inject(AuthService);
  private readonly cartService = inject(CartService);

  constructor() {
    // Keep the cart badge truthful: fetch the server cart when the user is
    // signed in, zero it when they sign out.
    effect(() => {
      if (this.authService.isAuthenticated()) {
        this.cartService.getCart().subscribe({
          error: () => this.cartService.reset(),
        });
      } else {
        this.cartService.reset();
      }
    });
  }
}
