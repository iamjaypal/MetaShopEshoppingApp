import { Routes } from '@angular/router';

import { adminGuard } from './core/guards/admin.guard';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },

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
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/cart/pages/cart/cart.component').then(c => c.CartPageComponent),
        title: 'Your cart · MetaShop',
      },
      {
        path: 'checkout',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/checkout/pages/checkout/checkout.component')
            .then(c => c.CheckoutPageComponent),
        title: 'Checkout · MetaShop',
      },
      {
        path: 'orders',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/orders/pages/order-list/order-list.component')
            .then(c => c.OrderListPageComponent),
        title: 'Your orders · MetaShop',
      },
      {
        path: 'orders/:id/pay',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/orders/pages/payment/payment.component')
            .then(c => c.PaymentPageComponent),
        title: 'Pay for order · MetaShop',
      },

      // ---- admin console (own shell, ADMIN only) ----
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./layout/admin-shell/admin-shell.component')
            .then(c => c.AdminShellComponent),
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/admin/pages/dashboard/dashboard.component')
                .then(c => c.DashboardComponent),
            title: 'Admin dashboard · MetaShop',
          },
          {
            path: 'products',
            loadComponent: () =>
              import('./features/admin/pages/products/products.component')
                .then(c => c.AdminProductsPageComponent),
            title: 'Admin · Products',
          },
          {
            path: 'inventory',
            loadComponent: () =>
              import('./features/admin/pages/inventory/inventory.component')
                .then(c => c.AdminInventoryPageComponent),
            title: 'Admin · Inventory',
          },
          {
            path: 'orders',
            loadComponent: () =>
              import('./features/admin/pages/orders/orders.component')
                .then(c => c.AdminOrdersPageComponent),
            title: 'Admin · Orders',
          },
          {
            path: 'payments',
            loadComponent: () =>
              import('./features/admin/pages/payments/payments.component')
                .then(c => c.AdminPaymentsPageComponent),
            title: 'Admin · Payments',
          },
          {
            path: 'users',
            loadComponent: () =>
              import('./features/admin/pages/users/users.component')
                .then(c => c.AdminUsersPageComponent),
            title: 'Admin · Users',
          },
        ],
      },

      { path: '', pathMatch: 'full', redirectTo: 'home' },
      { path: '**', redirectTo: 'home' },
    ],
  },

  { path: '**', redirectTo: 'home' },
];
