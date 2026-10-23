import {
  ChangeDetectionStrategy, Component, OnInit, inject, signal,
} from '@angular/core';
import {
  AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators,
} from '@angular/forms';
import { Subscription, timer } from 'rxjs';

import { Address, AddressRequest } from '../../../../core/models/address.model';
import { ApiError } from '../../../../core/models/api-error';
import { AddressService } from '../../../../core/services/address.service';
import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import {
  passwordStrength, passwordsMatch, strongPassword,
} from '../../../../shared/validators/password.validators';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';

type AccountTab = 'profile' | 'security' | 'addresses';

/**
 * The account hub: profile details, password and the address book —
 * one tab per concern.
 */
@Component({
  selector: 'app-account',
  standalone: true,
  imports: [ReactiveFormsModule, SkeletonComponent],
  templateUrl: './account.component.html',
  styleUrl: './account.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountComponent implements OnInit {

  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly addressService = inject(AddressService);

  readonly user = this.auth.currentUser;
  readonly signingOut = signal(false);
  readonly refreshFailed = signal(false);

  // ------------------------------------------------------------ tab state
  readonly tab = signal<AccountTab>('profile');

  setTab(tab: AccountTab): void {
    this.tab.set(tab);
    if (tab === 'addresses' && !this.addressesLoaded) {
      this.loadAddresses();
    }
  }

  // -------------------------------------------------------------- profile
  readonly savingProfile = signal(false);
  readonly profileForm = new FormGroup({
    firstName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(60)],
    }),
    lastName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(60)],
    }),
    phone: new FormControl('', {
      nonNullable: true,
      validators: [Validators.pattern(/^\+[0-9]{8,15}$/)],
    }),
  });

  // ------------------------------------------------------------- password
  readonly savingPassword = signal(false);
  readonly passwordForm = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, strongPassword()],
    }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  }, { validators: passwordsMatch() });

  // ------------------------------------------------------------ addresses
  readonly addresses = signal<Address[]>([]);
  readonly addressesLoading = signal(false);
  readonly addressesError = signal<string | null>(null);

  readonly showAddressForm = signal(false);
  readonly editingAddressId = signal<number | null>(null);
  readonly savingAddress = signal(false);

  /** Two-step delete: the address id whose button is armed. */
  readonly deleteArmedId = signal<number | null>(null);

  private deleteTimer: Subscription | null = null;

  readonly addressForm = new FormGroup({
    fullName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    phone: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\+[0-9]{8,15}$/)],
    }),
    line1: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    line2: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(120)] }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    state: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    postalCode: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{5,6}$/)],
    }),
    country: new FormControl('IN', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    isDefault: new FormControl(false, { nonNullable: true }),
  });

  readonly attemptedSubmit = signal(false);

  readonly countries = [
    { code: 'IN', label: 'India (+91)' },
    { code: 'AE', label: 'United Arab Emirates' },
    { code: 'AU', label: 'Australia' },
    { code: 'CA', label: 'Canada' },
    { code: 'DE', label: 'Germany' },
    { code: 'GB', label: 'United Kingdom' },
    { code: 'SG', label: 'Singapore' },
    { code: 'US', label: 'United States' },
  ];

  ngOnInit(): void {
    // Sync the profile silently; the navbar name must stay truthful.
    this.auth.refreshProfile().subscribe({
      next: user => this.prefillProfile(user),
      error: () => this.refreshFailed.set(true),
    });

    if (this.tab() === 'addresses') {
      this.loadAddresses();
    }
  }

  initials(): string {
    const user = this.user();
    if (!user) {
      return '?';
    }
    return (user.firstName.charAt(0) + user.lastName.charAt(0)).toUpperCase();
  }

  // ------------------------------------------------------------- profile

  private prefillProfile(user: { firstName: string; lastName: string; phone: string }): void {
    this.profileForm.setValue({
      firstName: user.firstName,
      lastName: user.lastName,
      phone: user.phone ?? '',
    });
  }

  hasError(control: AbstractControl): boolean {
    return control.invalid && (control.touched || this.attemptedSubmit());
  }

  saveProfile(): void {
    if (this.profileForm.invalid || this.savingProfile()) {
      this.attemptedSubmit.set(true);
      return;
    }

    this.savingProfile.set(true);
    this.auth.updateProfile(this.profileForm.getRawValue()).subscribe({
      next: user => {
        this.savingProfile.set(false);
        this.toast.success('Profile updated.');
        this.prefillProfile(user);
      },
      error: (error: ApiError) => {
        this.savingProfile.set(false);
        this.toast.error(error.message);
      },
    });
  }

  // ------------------------------------------------------------- password

  strength(): number {
    return passwordStrength(this.passwordForm.controls.newPassword.value);
  }

  savePassword(): void {
    if (this.passwordForm.invalid || this.savingPassword()) {
      this.attemptedSubmit.set(true);
      return;
    }

    const value = this.passwordForm.getRawValue();
    if (value.newPassword !== value.confirmPassword) {
      this.passwordForm.controls.confirmPassword.setErrors({ mismatch: true });
      return;
    }

    this.savingPassword.set(true);
    this.auth.changePassword({
      currentPassword: value.currentPassword,
      newPassword: value.newPassword,
    }).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.passwordForm.reset();
        this.toast.success('Password changed.');
      },
      error: (error: ApiError) => {
        this.savingPassword.set(false);
        this.toast.error(error.message);
      },
    });
  }

  // ------------------------------------------------------------ addresses

  loadAddresses(): void {
    this.addressesLoading.set(true);
    this.addressesError.set(null);

    this.addressService.getAddresses().subscribe({
      next: list => {
        this.addresses.set(list);
        this.addressesLoading.set(false);
        this.addressesLoaded = true;
      },
      error: (error: ApiError) => {
        this.addressesError.set(error.message);
        this.addressesLoading.set(false);
      },
    });
  }

  private addressesLoaded = false;

  startAddAddress(): void {
    this.editingAddressId.set(null);
    this.addressForm.reset({ country: 'IN', isDefault: false });
    this.attemptedSubmit.set(false);
    this.showAddressForm.set(true);
  }

  startEditAddress(address: Address): void {
    this.editingAddressId.set(address.id);
    this.addressForm.setValue({
      fullName: address.fullName,
      phone: address.phone,
      line1: address.line1,
      line2: address.line2,
      city: address.city,
      state: address.state,
      postalCode: address.postalCode,
      country: address.country,
      isDefault: address.isDefault,
    });
    this.attemptedSubmit.set(false);
    this.showAddressForm.set(true);
  }

  cancelAddressForm(): void {
    this.showAddressForm.set(false);
    this.editingAddressId.set(null);
  }

  submitAddress(): void {
    if (this.addressForm.invalid || this.savingAddress()) {
      this.attemptedSubmit.set(true);
      return;
    }

    const request: AddressRequest = this.addressForm.getRawValue();
    const editingId = this.editingAddressId();
    this.savingAddress.set(true);

    const call = editingId === null
      ? this.addressService.addAddress(request)
      : this.addressService.updateAddress(editingId, request);

    call.subscribe({
      next: address => {
        if (editingId === null) {
          this.addresses.update(list => [...list, address]);
        } else {
          this.addresses.update(list => list.map(item =>
            item.id === address.id ? address : item));
        }
        this.savingAddress.set(false);
        this.showAddressForm.set(false);
        this.editingAddressId.set(null);
        this.toast.success(editingId === null ? 'Address saved.' : 'Address updated.');
      },
      error: (error: ApiError) => {
        this.savingAddress.set(false);
        this.toast.error(error.message);
      },
    });
  }

  /** Two-step delete button: arm, then confirm within 3 seconds. */
  deleteAddress(address: Address): void {
    if (this.deleteArmedId() !== address.id) {
      this.deleteTimer?.unsubscribe();
      this.deleteArmedId.set(address.id);
      this.deleteTimer = timer(3000).subscribe(() => this.deleteArmedId.set(null));
      return;
    }

    this.deleteTimer?.unsubscribe();
    this.deleteArmedId.set(null);

    this.addressService.deleteAddress(address.id).subscribe({
      next: () => {
        this.addresses.update(list => list.filter(item => item.id !== address.id));
        this.toast.info('Address deleted.');
      },
      error: (error: ApiError) => this.toast.error(error.message),
    });
  }

  makeDefault(address: Address): void {
    this.addressService.setDefault(address.id).subscribe({
      next: updated => {
        // A new default demotes every other address.
        this.addresses.update(list => list.map(item => ({
          ...item,
          isDefault: item.id === updated.id,
        })));
        this.toast.success('Default address updated.');
      },
      error: (error: ApiError) => this.toast.error(error.message),
    });
  }

  // ---------------------------------------------------------------- auth

  signOut(): void {
    this.signingOut.set(true);
    this.auth.logout();
  }
}
