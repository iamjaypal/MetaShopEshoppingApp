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
| F3 | Product listing (filters, sort, search, pagination) + product detail (gallery, stock, add-to-cart) | ✅ this phase |
| F4 | Cart + checkout | ⬜ |
| F5 | Orders, payment page, profile/addresses | ⬜ |
| F6 | Admin console | ⬜ |

## Structure

```
src/app/
├── core/                     # the app-wide plumbing
│   ├── models/               # backend DTO mirrors (ApiResponse, ProductSummary, ProductDetail, Cart, …)
│   ├── services/             # AuthService, TokenStorage, ProductService, InventoryService, CartService, ToastService
│   ├── interceptors/         # auth.interceptor — Bearer header + error normalising
│   └── guards/               # authGuard (protected), guestGuard (login/register)
├── layout/                   # the storefront shell: navbar (sticky glass, user menu),
│   └── shell/                # footer; auth pages render outside it
├── shared/components/        # reusable UI (brand-logo, toast, product-card, skeleton)
└── features/
    ├── auth/pages/login/     # login  (.ts logic / .html template / .css unique styles)
    ├── auth/pages/register/  # signup
    ├── home/pages/home/      # storefront home: hero, categories, fresh arrivals
    ├── products/pages/product-list/    # catalogue: search, category/brand/price filters,
    │                         #           sort, pagination — all in the URL's query params
    ├── products/pages/product-detail/  # gallery, price/MRP, live stock chip, quantity,
    │                         #           add-to-cart, specs, related products
    ├── account/pages/account/# signed-in profile page
    └── misc/pages/coming-soon/  # honest placeholders for routes not built yet
```

## Styling rules

- **`src/styles.css` is the design system** — design tokens (colors, radii, shadows, fonts),
  buttons, form fields, cards, badges, banners, the auth split-screen layout and all shared
  animations live there, written exactly once.
- A component's `.css` file only contains what is unique to that component.
- Fonts: Inter (body) + Sora (display), loaded in `index.html`.
