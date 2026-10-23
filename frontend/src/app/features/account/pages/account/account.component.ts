import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import { ApiError } from '../../../../core/models/api-error';
import { UserSummary } from '../../../../core/models/user.model';


/**
 * Temporary signed-in landing page for Phase F1.
 *
 * It proves the whole auth loop against the real backend — token attached by
 * the interceptor, profile refresh from `/users/me`, sign-out — until the
 * storefront replaces it in the next phase.
 */
@Component({
  selector: 'app-account',
  standalone: true,
  imports: [],
  templateUrl: './account.component.html',
  styleUrl: './account.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountComponent {

  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly user = this.auth.currentUser;
  readonly signingOut = signal(false);
  readonly refreshFailed = signal(false);

  initials(): string {
    const user = this.user();
    if (!user) {
      return '?';
    }
    return (user.firstName.charAt(0) + user.lastName.charAt(0)).toUpperCase();
  }

  ngOnInit(): void {
    // Prove the interceptor + the profile endpoint work end-to-end.
    this.auth.refreshProfile().subscribe({
      next: (user: UserSummary) => this.toast.info(`Profile synced for ${user.email}`),
      error: (error: ApiError) => {
        this.refreshFailed.set(true);
        this.toast.error(error.message);
      },
    });
  }

  signOut(): void {
    this.signingOut.set(true);
    this.auth.logout();
  }
}
