import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApiResponse } from '../models/api-response.model';
import { Address, AddressRequest } from '../models/address.model';

/**
 * The signed-in user's address book (spec 4). Profile/address management
 * grows here in phase F5; checkout only needs to list and add today.
 */
@Injectable({ providedIn: 'root' })
export class AddressService {

  private readonly http = inject(HttpClient);

  /** All saved addresses, default address first on the backend. */
  getAddresses(): Observable<Address[]> {
    return this.http
      .get<ApiResponse<Address[]>>('/api/v1/users/me/addresses')
      .pipe(map(response => response.data));
  }

  /** Saves a new address and answers with it. */
  addAddress(request: AddressRequest): Observable<Address> {
    return this.http
      .post<ApiResponse<Address>>('/api/v1/users/me/addresses', request)
      .pipe(map(response => response.data));
  }

  /** Replaces an address and answers with the updated one. */
  updateAddress(id: number, request: AddressRequest): Observable<Address> {
    return this.http
      .put<ApiResponse<Address>>(`/api/v1/users/me/addresses/${id}`, request)
      .pipe(map(response => response.data));
  }

  /** Deletes an address (204). */
  deleteAddress(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/users/me/addresses/${id}`);
  }

  /** Makes an address the default one. */
  setDefault(id: number): Observable<Address> {
    return this.http
      .patch<ApiResponse<Address>>(`/api/v1/users/me/addresses/${id}/default`, {})
      .pipe(map(response => response.data));
  }
}
