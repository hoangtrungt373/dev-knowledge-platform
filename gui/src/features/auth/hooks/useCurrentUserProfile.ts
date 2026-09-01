import { Dispatch, SetStateAction, useEffect, useRef, useState } from 'react';
import { profileApi } from '../api/profileApi';
import { authService } from '../services/authService';
import { User } from '../types';
import { decodeJwtPayload } from '@shared/utils/jwt';

interface UseCurrentUserProfileResult {
  user: User | null;
  setUser: Dispatch<SetStateAction<User | null>>;
  loading: boolean;
}

/**
 * Fetches the caller's own profile once on mount, plus a JIT-username-drift correction: a
 * brand-new Google/Facebook login gets JIT-provisioned server-side with a derived username that
 * differs from Keycloak's own default (identity-service's `UserServiceImpl
 * .findOrCreateFromKeycloak` renames it away from `username == email` on first sight) — but the
 * access token this very request was authenticated with was minted *before* that rename, so its
 * `preferred_username` claim still says the old value. Left alone, the next authenticated call
 * anywhere in the app would see that stale claim and get JIT-synced back to it, silently
 * reverting the rename (the same staleness class `ProfilePage.tsx`'s own `handleSave` already
 * guards against for a manual edit). Comparing the claim against what this response just said
 * settles it before that can happen, regardless of which page happens to make the first
 * authenticated call.
 *
 * <p>Guarded against StrictMode's dev-mode double-invoke via a `hasFetchedRef` — the
 * `refreshAccessToken()` call below is a real token rotation, not just an idempotent GET, so it
 * can't safely run twice (a second concurrent refresh could hit an already-rotated-out refresh
 * token and fail). Same guard `AuthCallback.tsx`/`AdminAuthCallback.tsx` use for their own
 * one-time-use PKCE code exchange.
 *
 * <p>A failed fetch is swallowed silently on purpose — `profileApi.getCurrentUser` already passes
 * `showError` through to `httpClient`, which surfaces its own toast, and (for a real 401) either
 * silently refreshes or clears storage and redirects to `/login` itself. There is nothing left for
 * this hook to do on top of that.
 */
export function useCurrentUserProfile(showError: (message: string) => void): UseCurrentUserProfileResult {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const hasFetchedRef = useRef(false);

  useEffect(() => {
    if (hasFetchedRef.current) return;
    hasFetchedRef.current = true;

    (async () => {
      try {
        const me = await profileApi.getCurrentUser(showError);
        setUser(me);

        const accessToken = authService.getAccessToken();
        if (accessToken) {
          try {
            const { preferred_username } = decodeJwtPayload<{ preferred_username?: string }>(accessToken);
            if (preferred_username && preferred_username !== me.username) {
              await authService.refreshAccessToken();
            }
          } catch {
            // Best-effort — a decode failure just means the next natural token refresh catches up.
          }
        }
      } catch {
        // See this hook's own Javadoc — httpClient already handled notifying/redirecting.
      } finally {
        setLoading(false);
      }
    })();
  }, [showError]);

  return { user, setUser, loading };
}
