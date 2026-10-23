/**
 * Address book contracts, mirroring auth-service's `AddressRequest` /
 * `AddressResponse` (spec 4). Addresses are under `/api/v1/users/me/addresses`
 * and come wrapped in the usual `ApiResponse` envelope.
 */

/** One saved shipping address. */
export interface Address {
  id: number;
  fullName: string;
  phone: string;
  line1: string;
  line2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  isDefault: boolean;
}

/** Payload for creating (or editing) an address. */
export interface AddressRequest {
  fullName: string;
  phone: string;
  line1: string;
  line2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  isDefault: boolean;
}
