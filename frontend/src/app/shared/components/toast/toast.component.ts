import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { Toast, ToastService } from '../../../core/services/toast.service';

/**
 * Renders the app-wide toast queue (bottom-right, stacked). Place once, in
 * the root component. Each toast auto-dismisses after ~4s or on click.
 */
@Component({
  selector: 'app-toast',
  standalone: true,
  templateUrl: './toast.component.html',
  styleUrl: './toast.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastComponent {

  private readonly toastService = inject(ToastService);

  readonly toasts = this.toastService.toasts;

  dismiss(toast: Toast): void {
    this.toastService.dismiss(toast.id);
  }
}
