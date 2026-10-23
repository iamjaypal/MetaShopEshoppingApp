import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { TokenStorage } from '../services/token-storage.service';
import { ApiError } from '../models/api-error';

/**
 * Attaches the `Authorization: Bearer` header to every request and normalises
 * backend errors into {@link ApiError} with the envelope's `message`.
 *
 * A 401 on a protected call means the session is no longer valid: the local
 * session is cleared and the user is sent to sign in again. Responses to the
 * auth endpoints themselves are excluded — a wrong password (401 on /login)
 * must stay a form error, not a logout.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(TokenStorage);
  const router = inject(Router);

  const token = storage.getAccessToken();
  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && token && !req.url.includes('/api/v1/auth/')) {
        storage.clear();
        router.navigate(['/login'], { queryParams: { expired: 1 } });
      }

      const message = error.error?.message
        ?? fallbackMessage(error.status);

      return throwError(() => new ApiError(message, error.status));
    }),
  );
};

/** Clear, human-readable copy for failures that carry no envelope. */
function fallbackMessage(status: number): string {
  if (status === 0) {
    return 'Cannot reach the server. Check that the API gateway is running.';
  }
  if (status >= 500) {
    return 'Something went wrong on our side. Please try again.';
  }
  return 'Something went wrong. Please try again.';
}
