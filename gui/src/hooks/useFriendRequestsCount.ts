import { useCallback, useEffect, useState } from 'react';
import { friendApi } from '../api';
import { authService } from '../services';

/**
 * Incoming-friend-request count for the NavBar badge. Polls on an interval rather than needing a
 * websocket/push channel — friend requests are low-frequency enough that a short poll is simpler
 * and good enough for now.
 *
 * Guards on `authService.isAuthenticated()` before calling the endpoint at all — NavBar renders
 * (and this hook runs) on every route including /login, and an unauthenticated call to a
 * protected endpoint would trip httpClient's 401 handler, which redirects to /login. Checking
 * first avoids ever making that call instead of relying on the 401 fallback.
 */
export function useFriendRequestsCount(pollMs = 60_000): { count: number; refresh: () => void } {
  const [count, setCount] = useState(0);

  const refresh = useCallback(() => {
    if (!authService.isAuthenticated()) {
      setCount(0);
      return;
    }
    friendApi.listIncomingRequests({ page: 0, size: 1 })
      .then(data => setCount(data.totalElements))
      .catch(() => {
        // Silent — a failed badge refresh shouldn't surface a notification.
      });
  }, []);

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, pollMs);
    return () => clearInterval(id);
  }, [refresh, pollMs]);

  return { count, refresh };
}
