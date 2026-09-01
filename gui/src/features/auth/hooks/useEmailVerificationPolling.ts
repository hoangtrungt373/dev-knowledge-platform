import { Dispatch, SetStateAction, useEffect } from 'react';
import { profileApi } from '../api/profileApi';
import { authService } from '../services/authService';
import { User } from '../types';

/**
 * Clears `ProfilePage.tsx`'s email-verification banner without forcing a full re-login.
 * Verification status is a JWT claim baked into the access token at issuance time, so a plain
 * reload keeps showing the stale (unverified) value — Keycloak's own action-token link can't push
 * a change into an already-open tab. Refreshing gives a real `refresh_token` grant a chance to
 * pick up the new claim as soon as possible, without waiting for the current access token to
 * actually expire.
 *
 * <p>Runs once immediately (identity-service's `sendVerifyEmail` redirects back to `/dashboard`
 * after the Keycloak confirmation click, which is often a brand-new tab/page load — one that never
 * fires `visibilitychange` on its own) and again on every future tab-refocus (the case where the
 * link was opened in a separate tab and the user comes back to this already-open one).
 * Silent/best-effort — a failed check here just means the next trigger tries again. A no-op once
 * `user.emailVerified` is already `true`.
 */
export function useEmailVerificationPolling(user: User | null, setUser: Dispatch<SetStateAction<User | null>>): void {
  useEffect(() => {
    if (!user || user.emailVerified) return;

    const checkVerification = async () => {
      const refreshed = await authService.refreshAccessToken();
      if (!refreshed) return;
      try {
        const me = await profileApi.getCurrentUser();
        setUser(me);
      } catch {
        // Silent — next trigger retries.
      }
    };

    checkVerification();

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        checkVerification();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [user?.emailVerified, setUser]);
}
