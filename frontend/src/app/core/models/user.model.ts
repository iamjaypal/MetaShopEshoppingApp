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
  password: string;
}


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
