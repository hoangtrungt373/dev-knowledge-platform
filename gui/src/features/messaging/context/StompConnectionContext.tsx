import { createContext, ReactNode, useContext, useEffect, useRef, useState } from 'react';
import { authService } from '@auth/services/authService';
import { useNotification } from '@shared/contexts/NotificationContext';
import { stompSocket } from '../api/socket';
import { DmMessage, WsErrorPayload } from '../types';

interface StompConnectionContextValue {
  /** Whether the STOMP connection is currently up — gates the composer's send button. */
  connected: boolean;
  /** Publishes to an `/app/**` destination. */
  publish: (destination: string, body: unknown) => void;
  /**
   * Registers a listener for every DM delivered to this user's private queue, regardless of which
   * thread it belongs to. There is exactly one real STOMP subscription to `/user/queue/dms` no
   * matter how many components call this — `useDmThread`/`useDmThreads` each register their own
   * listener and filter by thread/recipient themselves.
   */
  onDmMessage: (listener: (message: DmMessage) => void) => () => void;
}

const StompConnectionContext = createContext<StompConnectionContextValue | undefined>(undefined);

export function useStompConnection(): StompConnectionContextValue {
  const ctx = useContext(StompConnectionContext);
  if (!ctx) {
    throw new Error('useStompConnection must be used within StompConnectionProvider');
  }
  return ctx;
}

/**
 * App-wide STOMP connection — one WebSocket for the whole app, opened once on login and closed on
 * logout, shared by every DM (and, in Phase 2, group/channel) view. Mirrors NotificationProvider's
 * shape (a top-level provider wrapping the whole app in App.tsx).
 */
export function StompConnectionProvider({ children }: { children: ReactNode }) {
  const { showError } = useNotification();
  const [connected, setConnected] = useState(false);
  const dmListeners = useRef(new Set<(message: DmMessage) => void>());

  useEffect(() => {
    if (!authService.isAuthenticated()) return;

    stompSocket.connect(() => authService.getAccessToken(), {
      onError: showError,
      onConnectionChange: setConnected,
    });

    const unsubscribeDms = stompSocket.subscribe<DmMessage>('/user/queue/dms', message => {
      dmListeners.current.forEach(listener => listener(message));
    });
    const unsubscribeErrors = stompSocket.subscribe<WsErrorPayload>('/user/queue/errors', payload => {
      showError(payload.message);
    });

    return () => {
      unsubscribeDms();
      unsubscribeErrors();
      stompSocket.disconnect();
    };
    // Runs once per mount (login/logout reloads the page via authService.logout's redirect, so a
    // fresh mount is how the connection picks up a new session — no need to react to token changes).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const value: StompConnectionContextValue = {
    connected,
    publish: (destination, body) => stompSocket.publish(destination, body),
    onDmMessage: listener => {
      dmListeners.current.add(listener);
      return () => dmListeners.current.delete(listener);
    },
  };

  return (
    <StompConnectionContext.Provider value={value}>{children}</StompConnectionContext.Provider>
  );
}
