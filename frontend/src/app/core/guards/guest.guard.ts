import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/**
 * Auth pages (/login, /register) are for signed-out users — anyone already
 * signed in who deep-links there is sent to the account page instead.
 */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.isAuthenticated()
    ? router.createUrlTree(['/account'])
    : true;
};
