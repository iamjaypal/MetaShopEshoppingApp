# MetaShop — frontend (Angular)

The MetaShop web client: an Angular 21 (standalone, signals, zoneless) single-page app that talks
to the microservices stack **through the API gateway** (`http://localhost:8080`), which is also
where CORS for this app's origin (`http://localhost:4200`) is configured.

## Run

```bash
cd frontend
pnpm install
pnpm start            # = ng serve, on http://localhost:4200
```

`pnpm` is the package manager this workspace uses (system `npm` 9.2.0 hits an npm bug with
Angular's dependency tree). If pnpm is missing: `npm install -g pnpm` (or `corepack enable pnpm`).

The backend must be running (see the root README's "Running the stack") — the auth pages call the
gateway live.

## Status (phase by phase)

| Phase | Scope | Status |
|---|---|---|
| F1 | Workspace + design system + auth (login, register) + account page | ✅ |
| F2 | App shell (navbar, footer) + storefront home page on live data | ✅ |
| F3 | Product listing (filters, sort, search, pagination) + product detail (gallery, stock, add-to-cart) | ✅ |
| F4 | Cart page + checkout (addresses, place order) | ✅ |
| F5 | Orders history, payment page (mock gateway), profile/addresses | ✅ |
| F6 | Admin console (dashboard, products, inventory, orders, payments, users) | ✅ this phase |

## Structure

```
src/app/
├── core/                     # the app-wide plumbing
│   ├── models/               # backend DTO mirrors (ApiResponse, ProductSummary, ProductDetail, Cart, Order, Payment, Address, …)
│   ├── services/             # AuthService, TokenStorage, ProductService, InventoryService, CartService, AddressService, OrderService, PaymentService, ToastService
│   ├── interceptors/         # auth.interceptor — Bearer header + error normalising
│   └── guards/               # authGuard, adminGuard (staff only), guestGuard (login/register)
├── layout/                   # the storefront shell: navbar (sticky glass, user menu),
│   ├── shell/                # footer; auth pages render outside it
│   └── admin-shell/          # dark sidebar layout for the admin console
├── shared/components/        # reusable UI (brand-logo, toast, product-card, skeleton)
├── shared/validators/        # shared form validators (password policy + match)
└── features/
    ├── auth/pages/login/     # login  (.ts logic / .html template / .css unique styles)
    ├── auth/pages/register/  # signup
    ├── home/pages/home/      # storefront home: hero, categories, fresh arrivals
    ├── products/pages/product-list/    # catalogue: search, category/brand/price filters,
    │                         #           sort, pagination — all in the URL's query params
    ├── products/pages/product-detail/  # gallery, price/MRP, live stock chip, quantity,
    │                         #           add-to-cart, specs, related products
    ├── cart/pages/cart/      # lines with steppers + remove, stock warnings,
    │                         #           free-shipping progress, checkout hand-off
    ├── checkout/pages/checkout/  # address book picker + add-address form, review,
    │                         #           place order → confirmation panel
    ├── orders/pages/order-list/  # paged history: status chips, pay/cancel actions
    ├── orders/pages/payment/ # mock gateway: method picker, capture/decline verdicts
    ├── account/pages/account/# tabs: profile details, password, address book CRUD
    └── admin/pages/          # admin console (behind adminGuard): dashboard,
                              #   products (+ editor drawer, images), inventory,
                              #   orders, payments ledger, users
    └── misc/pages/coming-soon/  # honest placeholders for routes not built yet
```

## Styling rules

- **`src/styles.css` is the design system** — design tokens (colors, radii, shadows, fonts),
  buttons, form fields, cards, badges, banners, the auth split-screen layout and all shared
  animations live there, written exactly once.
- A component's `.css` file only contains what is unique to that component.
- Fonts: Inter (body) + Sora (display), loaded in `index.html`.
