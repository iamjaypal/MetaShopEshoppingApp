import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/**
 * Admin-only area. Signed-out visitors go to login (with return URL);
 * signed-in non-admins are bounced to the home page with a notice.
 */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  if (!authService.isAuthenticated()) {
    return router.createUrlTree(['/login'], {
      queryParams: { returnUrl: router.url },
    });
  }

  if (!authService.isAdmin()) {
    toast.error('That area is for MetaShop staff only.');
    return router.createUrlTree(['/home']);
  }

  return true;
};
