import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

/**
 * Route table.
 *
 * - `/login` and `/register` render standalone (no navbar/footer).
 * - Everything else renders inside the storefront shell (navbar + footer):
 *   home, the catalogue and product pages today; cart/orders phase by phase —
 *   those routes still point at honest placeholders.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },

  // ---- auth (no shell) ----
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/pages/login/login.component').then(c => c.LoginComponent),
    title: 'Sign in · MetaShop',
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/pages/register/register.component').then(c => c.RegisterComponent),
    title: 'Create account · MetaShop',
  },

  // ---- storefront (shell) ----
  {
    path: '',
    loadComponent: () =>
      import('./layout/shell/shell.component').then(c => c.ShellComponent),
    children: [
      {
        path: 'home',
        loadComponent: () =>
          import('./features/home/pages/home/home.component').then(c => c.HomeComponent),
        title: 'MetaShop — Shop the future',
      },
      {
        path: 'account',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/account/pages/account/account.component').then(c => c.AccountComponent),
        title: 'My account · MetaShop',
      },

      // Real pages arrive phase by phase; the routes exist so navigation and
      // guards can already be built against them.
      {
        path: 'products',
        loadComponent: () =>
          import('./features/products/pages/product-list/product-list.component')
            .then(c => c.ProductListComponent),
        title: 'Shop all products · MetaShop',
      },
      {
        path: 'products/:id',
        loadComponent: () =>
          import('./features/products/pages/product-detail/product-detail.component')
            .then(c => c.ProductDetailComponent),
        title: 'Product · MetaShop',
      },
      {
        path: 'cart',
        loadComponent: () =>
          import('./features/misc/pages/coming-soon/coming-soon.component')
            .then(c => c.ComingSoonComponent),
        data: { title: 'Your cart', phase: 'F4' },
        title: 'Cart · MetaShop',
      },
      {
        path: 'orders',
        loadComponent: () =>
          import('./features/misc/pages/coming-soon/coming-soon.component')
            .then(c => c.ComingSoonComponent),
        data: { title: 'Your orders', phase: 'F5' },
        title: 'Orders · MetaShop',
      },

      { path: '', pathMatch: 'full', redirectTo: 'home' },
      { path: '**', redirectTo: 'home' },
    ],
  },

  { path: '**', redirectTo: 'home' },
];
