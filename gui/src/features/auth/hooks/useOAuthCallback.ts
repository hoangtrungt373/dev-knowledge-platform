import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useNotification } from '@shared/contexts/NotificationContext';

interface UseOAuthCallbackOptions {
  /** Exchanges the code/state (and completes login) — throws with a user-facing message on failure. */
  exchange: (code: string | null, state: string | null, error?: string | null) => Promise<void>;
  /** Route to land on once `exchange` succeeds. */
  successPath: string;
  /** Route to bounce back to (after a short delay) once `exchange` throws. */
  errorPath: string;
  /** Toast shown on success. */
  successMessage?: string;
  /** Shown when the thrown error has no message of its own. */
  fallbackErrorMessage?: string;
}

/**
 * Shared Authorization Code + PKCE callback flow — `AuthCallback.tsx` (social/regular login) and
 * `AdminAuthCallback.tsx` (admin login) were otherwise near-identical: read `code`/`state`/`error`
 * off the query string, exchange exactly once, toast + navigate on success, or show the error and
 * bounce back after a delay on failure.
 *
 * <p>Guarded against StrictMode's dev-mode double-invoke via a `hasRun` ref — the authorization
 * code and PKCE verifier are both one-time-use, so a second invocation would fail (code/verifier
 * already consumed) and bounce back to `errorPath` even though the first call already succeeded.
 */
export function useOAuthCallback({
  exchange,
  successPath,
  errorPath,
  successMessage = 'Login successful!',
  fallbackErrorMessage = 'Login failed',
}: UseOAuthCallbackOptions): { error: string | null } {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const { showError, showSuccess } = useNotification();
  const hasRun = useRef(false);

  useEffect(() => {
    if (hasRun.current) return;
    hasRun.current = true;

    const run = async () => {
      try {
        const code = searchParams.get('code');
        const state = searchParams.get('state');
        const errorParam = searchParams.get('error');

        await exchange(code, state, errorParam);

        showSuccess(successMessage);
        navigate(successPath, { replace: true });
      } catch (err) {
        const message = err instanceof Error ? err.message : fallbackErrorMessage;
        setError(message);
        showError(message);
        setTimeout(() => {
          navigate(errorPath, { replace: true });
        }, 2000);
      }
    };

    run();
  }, [navigate, searchParams, showError, showSuccess, exchange, successPath, errorPath, successMessage, fallbackErrorMessage]);

  return { error };
}
