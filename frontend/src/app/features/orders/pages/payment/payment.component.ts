import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiError } from '../../../../core/models/api-error';
import { Order, Payment, PaymentMethod } from '../../../../core/models/order.model';
import { OrderService } from '../../../../core/services/order.service';
import { PaymentService } from '../../../../core/services/payment.service';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';
import { ToastService } from '../../../../core/services/toast.service';

/** The mock gateway's methods, shown as pickable tiles. */
const PAYMENT_METHODS: Array<{ value: PaymentMethod; label: string; hint: string }> = [
  { value: 'CARD', label: 'Card', hint: 'Credit or debit — simulated instantly' },
  { value: 'UPI', label: 'UPI', hint: 'Pay from any UPI app — simulated' },
  { value: 'WALLET', label: 'Wallet', hint: 'MetaShop balance — simulated' },
];

/**
 * Pay for one order: pick a method, run the (mock) charge and show the
 * honest outcome — a capture confirms the order, a decline cancels it.
 */
@Component({
  selector: 'app-payment',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, RouterLink, SkeletonComponent],
  templateUrl: './payment.component.html',
  styleUrl: './payment.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentPageComponent {

  private readonly orderService = inject(OrderService);
  private readonly paymentService = inject(PaymentService);
  private readonly toastService = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly methods = PAYMENT_METHODS;

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly order = signal<Order | null>(null);

  readonly selectedMethod = signal<PaymentMethod>('CARD');
  readonly paying = signal(false);

  /** The payment attempt once it has a verdict. */
  readonly payment = signal<Payment | null>(null);
  readonly paymentSuccess = signal(false);

  /** The amount due — the order's authoritative total. */
  readonly amount = computed(() => this.order()?.total ?? 0);

  constructor() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (Number.isInteger(id) && id > 0) {
      this.load(id);
    } else {
      this.loadError.set('That payment link looks broken.');
      this.loading.set(false);
    }
  }

  load(id: number): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.order.set(null);
    this.payment.set(null);
    window.scrollTo({ top: 0 });

    this.orderService.getOrder(id).subscribe({
      next: order => {
        this.order.set(order);
        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.loadError.set(error.status === 404
          ? 'We couldn\u2019t find that order.'
          : error.message);
        this.loading.set(false);
      },
    });
  }

  selectMethod(method: PaymentMethod): void {
    this.selectedMethod.set(method);
  }

  pay(): void {
    const order = this.order();
    if (!order || this.paying()) {
      return;
    }

    this.paying.set(true);
    this.paymentService.pay(order.id, this.selectedMethod()).subscribe({
      next: response => {
        this.paying.set(false);
        this.payment.set(response.data);
        this.paymentSuccess.set(response.success);

        if (response.success) {
          this.toastService.success('Payment captured — your order is confirmed!');
        } else {
          // A decline cancels the order and releases the stock (backend rule).
          this.toastService.error(response.message || 'Payment declined.');
        }
        window.scrollTo({ top: 0 });
      },
      error: (error: ApiError) => {
        this.paying.set(false);
        this.toastService.error(error.message);
      },
    });
  }

  backToOrders(): void {
    this.router.navigate(['/orders']);
  }
}
