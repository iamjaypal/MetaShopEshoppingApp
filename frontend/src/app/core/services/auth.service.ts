import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, map, of } from 'rxjs';

import { TokenStorage } from './token-storage.service';
import { ApiResponse } from '../models/api-response.model';
import { LoginRequest, LoginResponse, RegisterRequest, RegisterResponse,
  UpdateProfileRequest, ChangePasswordRequest, UserSummary }
  from '../models/user.model';

/**
 * The session state + the calls to auth-service (`/api/v1/auth/**`).
 *
 * The current user is a signal so every component reading it re-renders
 * automatically when the session changes (sign-in, profile refresh, sign-out).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly storage = inject(TokenStorage);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly currentUserSignal = signal<UserSummary | null>(this.storage.getUser());

  /** The signed-in user (null when signed out). */
  readonly currentUser = computed(() => this.currentUserSignal());

  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);

  readonly isAdmin = computed(() =>
    this.currentUserSignal()?.roles?.includes('ADMIN') ?? false);

  private readonly API = '/api/v1/auth';

  /**
   * Signs in, persists the session and publishes the user.
   * Resolves with the verified user so callers can toast/navigate.
   */
  login(request: LoginRequest): Observable<UserSummary> {
    return this.http.post<ApiResponse<LoginResponse>>(`${this.API}/login`, request)
      .pipe(map(response => {
        const session = response.data;
        this.storage.saveSession(session.accessToken, session.refreshToken, session.user);
        this.currentUserSignal.set(session.user);
        return session.user;
      }));
  }

  /**
   * Creates the account. The backend does not return tokens for register
   * (sign in explicitly afterwards), so this resolves and the caller routes
   * to the login page.
   */
  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<ApiResponse<RegisterResponse>>(`${this.API}/register`, request)
      .pipe(map(response => response.data));
  }

  /** Re-reads the profile from the backend and republishes it. */
  refreshProfile(): Observable<UserSummary> {
    return this.http.get<ApiResponse<UserSummary>>('/api/v1/users/me')
      .pipe(map(response => {
        this.storage.saveUser(response.data);
        this.currentUserSignal.set(response.data);
        return response.data;
      }));
  }

  /** Updates name/phone and republishes the session user. */
  updateProfile(request: UpdateProfileRequest): Observable<UserSummary> {
    return this.http.put<ApiResponse<UserSummary>>('/api/v1/users/me', request)
      .pipe(map(response => {
        this.storage.saveUser(response.data);
        this.currentUserSignal.set(response.data);
        return response.data;
      }));
  }

  /** Changes the password (backend verifies the current one). */
  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.put<ApiResponse<void>>('/api/v1/users/me/password', request)
      .pipe(map(() => undefined));
  }

  /**
   * Signs out: tells the backend to revoke the refresh token (best effort —
   * the local session is cleared either way), then clears state.
   */
  logout(): void {
    this.http.post<ApiResponse<void>>(`${this.API}/logout`, {})
      .pipe(catchError(() => of(null)))
      .subscribe(() => this.endLocalSession());
  }

  /** Clears the local session without calling the backend. */
  endLocalSession(): void {
    this.storage.clear();
    this.currentUserSignal.set(null);
    this.router.navigate(['/login']);
  }
}
