import { useCallback, useRef, useState } from 'react';

/**
 * Prevents duplicate submissions from double-clicks or rapid re-calls.
 * Uses a ref for the in-flight check (stable guard reference) and state
 * for the loading flag exposed to the UI.
 *
 * Usage:
 *   const { loading, guard } = useSubmitGuard();
 *   const handleSubmit = (e) => { e.preventDefault(); guard(async () => { ... }); };
 *   <Button disabled={loading}>Submit</Button>
 */
export function useSubmitGuard() {
  const inFlight = useRef(false);
  const [loading, setLoading] = useState(false);

  const guard = useCallback(async (action: () => Promise<void>): Promise<void> => {
    if (inFlight.current) return;
    inFlight.current = true;
    setLoading(true);
    try {
      await action();
    } finally {
      inFlight.current = false;
      setLoading(false);
    }
  }, []);

  return { loading, guard };
}
