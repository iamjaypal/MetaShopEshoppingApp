/**
 * Auth-related contracts, mirroring auth-service's DTOs exactly
 * (`UserSummary`, `LoginResponse`, `RegisterResponse`, …).
 */

/** Who the caller is — matches auth-service `UserSummary`. */
export interface UserSummary {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  enabled: boolean;
  emailVerified: boolean;
  roles: string[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  password: string;
}

/** Matches auth-service `RegisterResponse` (no tokens — register then sign in). */
export interface RegisterResponse {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
}

/** Matches auth-service `LoginResponse`. */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

/** Matches auth-service `UpdateProfileRequest` (name/phone only). */
export interface UpdateProfileRequest {
  firstName: string;
  lastName: string;
  phone: string;
}

/** Matches auth-service `ChangePasswordRequest`. */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
