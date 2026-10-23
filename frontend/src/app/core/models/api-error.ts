/**
 * Error thrown by the HTTP interceptor after normalising the backend's
 * `ApiResponse` envelope into a single readable message. Components catch
 * this and show `error.message` directly.
 */
export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = 'ApiError';
  }
}
