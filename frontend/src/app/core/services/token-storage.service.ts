import { Injectable } from '@angular/core';

import { UserSummary } from '../models/user.model';

/**
 * The browser-side session store (localStorage).
 *
 * Only this class knows the storage keys, so rotating the mechanism
 * (e.g. to sessionStorage or cookies) touches exactly one file.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {

  private static readonly ACCESS_KEY = 'metashop.accessToken';
  private static readonly REFRESH_KEY = 'metashop.refreshToken';
  private static readonly USER_KEY = 'metashop.user';

  getAccessToken(): string | null {
    return localStorage.getItem(TokenStorage.ACCESS_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(TokenStorage.REFRESH_KEY);
  }

  getUser(): UserSummary | null {
    const raw = localStorage.getItem(TokenStorage.USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as UserSummary;
    } catch {
      return null; // corrupted entry — treat as signed out
    }
  }

  saveSession(accessToken: string, refreshToken: string, user: UserSummary): void {
    localStorage.setItem(TokenStorage.ACCESS_KEY, accessToken);
    localStorage.setItem(TokenStorage.REFRESH_KEY, refreshToken);
    localStorage.setItem(TokenStorage.USER_KEY, JSON.stringify(user));
  }

  /** Re-writes just the cached profile (after a profile refresh). */
  saveUser(user: UserSummary): void {
    localStorage.setItem(TokenStorage.USER_KEY, JSON.stringify(user));
  }

  clear(): void {
    localStorage.removeItem(TokenStorage.ACCESS_KEY);
    localStorage.removeItem(TokenStorage.REFRESH_KEY);
    localStorage.removeItem(TokenStorage.USER_KEY);
  }
}
