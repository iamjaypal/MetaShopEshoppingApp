import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators,
  ValidationErrors,
} from '@angular/forms';

import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import { ApiError } from '../../../../core/models/api-error';
import { BrandLogoComponent } from '../../../../shared/components/brand-logo/brand-logo.component';

/**
 * The sign-up page. Creates the account, then routes to the login page with
 * the email pre-filled (the backend returns no tokens on register — signing
 * in is an explicit step).
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, BrandLogoComponent],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent {

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);
  readonly shake = signal(false);
  readonly showPassword = signal(false);
  private readonly attemptedSubmit = signal(false);

  readonly form = new FormGroup({
    firstName: new FormControl('', [
      Validators.required,
      Validators.maxLength(50),
    ]),
    lastName: new FormControl('', [
      Validators.required,
      Validators.maxLength(50),
    ]),
    email: new FormControl('', [Validators.required, Validators.email]),
    phone: new FormControl('', [
      Validators.required,
      // auth-service requires international format, e.g. +919876543210
      Validators.pattern(/^\+[1-9]\d{7,14}$/),
    ]),
    password: new FormControl('', [Validators.required, strongPassword()]),
    confirmPassword: new FormControl('', [Validators.required]),
    terms: new FormControl(false, [Validators.requiredTrue]),
  }, { validators: passwordsMatch() });

  /** 0–4 password strength score, computed live from the value. */
  strength(): number {
    const password = this.form.controls.password.value ?? '';
    if (!password) {
      return 0;
    }
    let score = 0;
    if (password.length >= 8) score++;
    if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
    if (/\d/.test(password)) score++;
    if (/[^A-Za-z0-9]/.test(password)) score++;
    return score;
  }

  hasError(control: AbstractControl): boolean {
    return control.invalid && (control.touched || this.attemptedSubmit());
  }

  togglePassword(): void {
    this.showPassword.update(visible => !visible);
  }

  onSubmit(): void {
    this.attemptedSubmit.set(true);

    if (this.form.invalid || this.submitting()) {
      this.refuse(this.firstProblem());
      return;
    }

    this.submitting.set(true);
    this.formError.set(null);

    this.auth.register({
      firstName: this.form.controls.firstName.value!.trim(),
      lastName: this.form.controls.lastName.value!.trim(),
      email: this.form.controls.email.value!.trim(),
      phone: this.form.controls.phone.value!.trim(),
      password: this.form.controls.password.value!,
    }).subscribe({
      next: account => {
        this.toast.success(`Account created — welcome to MetaShop, ${account.firstName}!`);
        this.router.navigate(['/login'], {
          queryParams: { email: account.email },
        });
      },
      error: (error: ApiError) => this.refuse(error.message),
    });
  }

  /** The most useful message for the current invalid state. */
  private firstProblem(): string {
    const controls = this.form.controls;
    if (controls.terms.invalid) {
      return 'Please accept the terms to continue.';
    }
    if (this.form.hasError('mismatch')) {
      return 'The two passwords do not match.';
    }
    return 'Please fill the highlighted fields correctly.';
  }

  private refuse(message: string): void {
    this.submitting.set(false);
    this.formError.set(message);
    this.shake.set(true);
    setTimeout(() => this.shake.set(false), 500);
  }
}

/** The two password fields must agree. */
function passwordsMatch(): (group: AbstractControl) => ValidationErrors | null {
  return group => {
    const password = group.get('password')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return password && confirm && password !== confirm
      ? { mismatch: true }
      : null;
  };
}

/** 8+ chars with upper, lower and a digit — matches auth-service's policy. */
function strongPassword(): (control: AbstractControl) => ValidationErrors | null {
  return control => {
    const value = String(control.value ?? '');
    if (!value) {
      return null; // 'required' covers the empty case
    }
    const valid = value.length >= 8
      && /[a-z]/.test(value)
      && /[A-Z]/.test(value)
      && /\d/.test(value);
    return valid ? null : { weak: true };
  };
}
