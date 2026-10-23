import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators,
} from '@angular/forms';

import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import { ApiError } from '../../../../core/models/api-error';
import { BrandLogoComponent } from '../../../../shared/components/brand-logo/brand-logo.component';

/**
 * The sign-in page. On success the session is stored and the user is sent to
 * `returnUrl` (or the account page). A refused sign-in shakes the card and
 * shows the backend's message inline.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, BrandLogoComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);
  readonly shake = signal(false);
  readonly showPassword = signal(false);
  private readonly attemptedSubmit = signal(false);

  readonly form = new FormGroup({
    email: new FormControl('', [Validators.required, Validators.email]),
    password: new FormControl('', [Validators.required]),
  });

  constructor() {
    // Arriving from a link that carries context (e.g. ?email= after
    // registering) — pre-fill what we were given.
    const email = this.route.snapshot.queryParamMap.get('email');
    if (email) {
      this.form.controls.email.setValue(email);
    }
  }

  /** A field shows its error once touched or after a submit attempt. */
  hasError(control: AbstractControl): boolean {
    return control.invalid && (control.touched || this.attemptedSubmit());
  }

  togglePassword(): void {
    this.showPassword.update(visible => !visible);
  }

  onSubmit(): void {
    this.attemptedSubmit.set(true);

    if (this.form.invalid || this.submitting()) {
      this.refuse('Please enter your email and password.');
      return;
    }

    this.submitting.set(true);
    this.formError.set(null);

    const request = {
      email: this.form.controls.email.value!.trim(),
      password: this.form.controls.password.value!,
    };

    this.auth.login(request).subscribe({
      next: user => {
        this.toast.success(`Welcome back, ${user.firstName}!`);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/account';
        this.router.navigateByUrl(returnUrl);
      },
      error: (error: ApiError) => this.refuse(error.message),
    });
  }

  /** Shows the failure inline, shakes the card, and stops the spinner. */
  private refuse(message: string): void {
    this.submitting.set(false);
    this.formError.set(message);
    this.shake.set(true);
    setTimeout(() => this.shake.set(false), 500);
  }
}
