const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Lightweight client-side sanity check only, never the real validation on its own — every backend
 * endpoint that accepts an email re-validates server-side (e.g. `jakarta.validation`'s `@Email`).
 * Was previously duplicated as an identical inline regex in four places (`@auth`'s `Login.tsx`/
 * `SignUp.tsx`, `@ecommerce`'s `CheckoutPage.tsx`/`AddressFormDialog.tsx`); this is the one copy.
 */
export function isValidEmail(value: string): boolean {
  return EMAIL_PATTERN.test(value);
}
