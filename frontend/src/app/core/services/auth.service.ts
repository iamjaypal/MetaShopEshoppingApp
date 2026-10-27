import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, map, of } from 'rxjs';

import { TokenStorage } from './token-storage.service';
import { ApiResponse } from '../models/api-response.model';
import { LoginRequest, LoginResponse, RegisterRequest, RegisterResponse,
  UpdateProfileRequest, ChangePasswordRequest, UserSummary }
  from '../models/user.model';

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

  login(request: LoginRequest): Observable<UserSummary> {
    return this.http.post<ApiResponse<LoginResponse>>(`${this.API}/login`, request)
      .pipe(map(response => {
        const session = response.data;
        this.storage.saveSession(session.accessToken, session.refreshToken, session.user);
        this.currentUserSignal.set(session.user);
        return session.user;
      }));
  }


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

  logout(): void {
    const refreshToken = this.storage.getRefreshToken();
    const revoke = refreshToken
      ? this.http.post<ApiResponse<void>>(`${this.API}/logout`, { refreshToken })
          .pipe(catchError(() => of(null)))
      : of(null);

    revoke.subscribe(() => this.endLocalSession());
  }

  /** Clears the local session without calling the backend. */
  endLocalSession(): void {
    this.storage.clear();
    this.currentUserSignal.set(null);
    this.router.navigate(['/login']);
  }
}
