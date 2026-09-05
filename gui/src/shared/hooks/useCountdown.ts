import { useEffect, useState } from 'react';

interface Countdown {
  /** Never negative — clamped to 0 once `targetIso` has passed. */
  remainingMs: number;
  /** True once `remainingMs` has reached 0. Always `false` when `targetIso` is `null` — "no
   * target" isn't the same thing as "already expired." */
  expired: boolean;
}

/**
 * A live mm:ss-precision countdown to `targetIso` (an ISO instant) — ticks once a second while a
 * target is set, so callers get a genuine ticking clock rather than a coarser, cheaper-but-less-
 * immediate update. `targetIso: null` means "nothing to count down to" (e.g. the order this backs
 * isn't `PAYMENT_PROCESSING` right now) — `remainingMs` stays `0`/`expired` stays `false` in that
 * case, not a false "expired" reading.
 */
export function useCountdown(targetIso: string | null): Countdown {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!targetIso) return;
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(interval);
  }, [targetIso]);

  if (!targetIso) {
    return { remainingMs: 0, expired: false };
  }
  const remainingMs = Math.max(0, new Date(targetIso).getTime() - now);
  return { remainingMs, expired: remainingMs <= 0 };
}
