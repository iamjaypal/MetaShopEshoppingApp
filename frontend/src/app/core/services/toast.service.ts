import { Injectable, signal } from '@angular/core';

/** One toast notification. */
export interface Toast {
  id: number;
  type: 'success' | 'error' | 'info';
  message: string;
}

/**
 * App-wide toast notifications. Any component injects this and calls
 * `show(...)`; the single `<app-toast>` container in the root component
 * renders whatever is queued.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {

  private static readonly AUTO_DISMISS_MS = 4200;

  private nextId = 0;

  /** The live queue — the toast container reads this directly. */
  readonly toasts = signal<Toast[]>([]);

  success(message: string): void {
    this.show('success', message);
  }

  error(message: string): void {
    this.show('error', message);
  }

  info(message: string): void {
    this.show('info', message);
  }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(toast => toast.id !== id));
  }

  private show(type: Toast['type'], message: string): void {
    const toast: Toast = { id: ++this.nextId, type, message };

    this.toasts.update(list => [...list.slice(-3), toast]); // keep at most 4

    setTimeout(() => this.dismiss(toast.id), ToastService.AUTO_DISMISS_MS);
  }
}
