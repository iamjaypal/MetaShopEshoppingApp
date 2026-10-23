import {
  ChangeDetectionStrategy, Component, computed, inject, signal,
} from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { Address, AddressRequest } from '../../../../core/models/address.model';
import { ApiError } from '../../../../core/models/api-error';
import { Cart } from '../../../../core/models/cart.model';
import { Order } from '../../../../core/models/order.model';
import { AddressService } from '../../../../core/services/address.service';
import { CartService } from '../../../../core/services/cart.service';
import { OrderService } from '../../../../core/services/order.service';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';
import { ToastService } from '../../../../core/services/toast.service';

/** Order rules mirrored from order-service (spec 11), for the summary. */
const FREE_SHIPPING_THRESHOLD = 499;
const SHIPPING_FEE = 49;

/** The checkout: pick a delivery address, review the cart, place the order. */
@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink, SkeletonComponent],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutPageComponent {

  private readonly cartService = inject(CartService);
  private readonly addressService = inject(AddressService);
  private readonly orderService = inject(OrderService);
  private readonly toastService = inject(ToastService);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly cart = signal<Cart | null>(null);
  readonly addresses = signal<Address[]>([]);
  readonly selectedAddressId = signal<number | null>(null);

  readonly showAddressForm = signal(false);
  readonly savingAddress = signal(false);

  readonly placing = signal(false);
  readonly placedOrder = signal<Order | null>(null);

  readonly freeShippingThreshold = FREE_SHIPPING_THRESHOLD;
  readonly shippingFee = SHIPPING_FEE;

  readonly hasFreeShipping = computed(() =>
    (this.cart()?.subtotal ?? 0) >= FREE_SHIPPING_THRESHOLD);

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

  constructor() {
    this.load();
  }

  // ------------------------------------------------------------- loading

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);

    // Cart and address book are independent — load both, fail on either.
    forkJoin({
      cart: this.cartService.getCart(),
      addresses: this.addressService.getAddresses(),
    }).subscribe({
      next: ({ cart, addresses }) => {
        this.cart.set(cart);
        this.addresses.set(addresses);

        const preferred = addresses.find(address => address.isDefault) ?? addresses[0];
        if (preferred) {
          this.selectedAddressId.set(preferred.id);
        } else {
          this.showAddressForm.set(true); // nothing to pick from — open the form
        }

        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.loadError.set(error.message);
        this.loading.set(false);
      },
    });
  }

  // ------------------------------------------------------ address picking

  selectAddress(id: number): void {
    this.selectedAddressId.set(id);
  }

  toggleAddressForm(): void {
    this.showAddressForm.update(open => !open);
  }

  hasError(control: AbstractControl): boolean {
    return control.invalid && (control.touched || this.attemptedSubmit());
  }

  submitAddress(): void {
    if (this.addressForm.invalid || this.savingAddress()) {
      this.attemptedSubmit.set(true);
      return;
    }

    const request: AddressRequest = this.addressForm.getRawValue();
    this.savingAddress.set(true);

    this.addressService.addAddress(request).subscribe({
      next: address => {
        this.addresses.update(list => [...list, address]);
        this.selectedAddressId.set(address.id);
        this.showAddressForm.set(false);
        this.savingAddress.set(false);
        this.addressForm.reset({ country: 'IN', isDefault: false });
        this.attemptedSubmit.set(false);
        this.toastService.success('Address saved.');
      },
      error: (error: ApiError) => {
        this.savingAddress.set(false);
        this.toastService.error(error.message);
      },
    });
  }

  // ---------------------------------------------------------- place order

  placeOrder(): void {
    const addressId = this.selectedAddressId();
    if (addressId === null || this.placing()) {
      this.toastService.error('Choose a delivery address first.');
      return;
    }

    this.placing.set(true);
    this.orderService.createOrder(addressId).subscribe({
      next: order => {
        this.placing.set(false);
        this.placedOrder.set(order);
        window.scrollTo({ top: 0 });
      },
      error: (error: ApiError) => {
        this.placing.set(false);
        this.toastService.error(error.message);
        // Stock may have moved under us — refresh the cart quietly.
        this.cartService.getCart().subscribe();
      },
    });
  }
}
