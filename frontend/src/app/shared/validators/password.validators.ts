import { AbstractControl, ValidationErrors } from '@angular/forms';

/**
 * Password rules shared by the register and account pages. The policy
 * matches auth-service: 8+ characters with upper case, lower case and a digit.
 */

/** 8+ chars with upper, lower and a digit — matches auth-service's policy. */
export function strongPassword(): (control: AbstractControl) => ValidationErrors | null {
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

/** Group validator: the two password fields must agree. */
export function passwordsMatch(): (group: AbstractControl) => ValidationErrors | null {
  return group => {
    const password = group.get('password')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return password && confirm && password !== confirm
      ? { mismatch: true }
      : null;
  };
}

/** 0–4 live strength score for the meter UI. */
export function passwordStrength(value: string | null | undefined): number {
  if (!value) {
    return 0;
  }
  let score = 0;
  if (value.length >= 8) {
    score++;
  }
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) {
    score++;
  }
  if (/\d/.test(value)) {
    score++;
  }
  if (/[^A-Za-z0-9]/.test(value)) {
    score++;
  }
  return score;
}
