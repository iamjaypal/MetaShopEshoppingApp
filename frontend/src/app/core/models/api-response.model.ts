/**
 * The response envelope every MetaShop backend endpoint uses (spec 5.3):
 * `{ success, message, data }`.
 */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}
