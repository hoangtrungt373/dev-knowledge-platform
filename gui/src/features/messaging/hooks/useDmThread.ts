import { useCallback, useEffect, useRef, useState } from 'react';
import { authService } from '@auth/services/authService';
import { useNotification } from '@shared/contexts/NotificationContext';
import { dmApi } from '../api/dmApi';
import { useStompConnection } from '../context/StompConnectionContext';
import { DmMessage } from '../types';

export type DmTarget =
  | { kind: 'thread'; threadId: number; recipientUuid: string }
  | { kind: 'new'; recipientUuid: string };

/**
 * One open DM conversation — either an existing thread (history via REST + live filter by
 * threadId), a brand-new one with no thread yet (no history to load; the first echoed message
 * resolves the threadId via `onThreadResolved`), or `null` when no conversation is selected (the
 * hook then no-ops entirely — no subscription matching, `send` is a no-op).
 *
 * `send` always publishes over STOMP, never appends locally — the backend echoes every message
 * back to its sender (`convertAndSendToUser` to both participants), so the sent message appears
 * through the same live-message path as a message from the other participant.
 */
export function useDmThread(
  target: DmTarget | null,
  onThreadResolved?: (threadId: number) => void,
): { messages: DmMessage[]; loading: boolean; send: (content: string) => void } {
  const { showError } = useNotification();
  const { publish, onDmMessage } = useStompConnection();
  const [messages, setMessages] = useState<DmMessage[]>([]);
  const [loading, setLoading] = useState(target?.kind === 'thread');

  const onThreadResolvedRef = useRef(onThreadResolved);
  onThreadResolvedRef.current = onThreadResolved;
  // Tracks which threadId's history is currently loaded, so resolving a 'new' conversation into
  // its now-known threadId doesn't re-fetch (and briefly blank) messages already delivered live.
  const loadedThreadIdRef = useRef<number | null>(null);

  const targetKey = !target ? 'none' : target.kind === 'thread' ? `thread:${target.threadId}` : `new:${target.recipientUuid}`;

  useEffect(() => {
    if (target?.kind === 'thread' && loadedThreadIdRef.current === target.threadId) {
      return;
    }
    setMessages([]);
    loadedThreadIdRef.current = null;

    if (target?.kind !== 'thread') {
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    dmApi.listMessages(target.threadId, { page: 0, size: 50 }, showError)
      .then(data => {
        if (cancelled) return;
        // Backend returns most-recent-first; render chronologically.
        setMessages([...data.content].reverse());
        loadedThreadIdRef.current = target.threadId;
      })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [targetKey]);

  useEffect(() => {
    if (!target) return;
    const myUuid = authService.getUserUuid();

    return onDmMessage(message => {
      const matches = target.kind === 'thread'
        ? message.threadId === target.threadId
        // A 'new' target has no threadId yet: match the other participant's reply, or our own
        // echoed send. Assumes at most one pending "new conversation" composer is open at a time
        // (true for this single-page layout) — otherwise a second concurrent new conversation's
        // own echo would be indistinguishable from this one's.
        : message.sender.userUuid === target.recipientUuid || message.sender.userUuid === myUuid;

      if (!matches) return;

      setMessages(prev => [...prev, message]);

      if (target.kind === 'new') {
        loadedThreadIdRef.current = message.threadId;
        onThreadResolvedRef.current?.(message.threadId);
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [onDmMessage, targetKey]);

  const send = useCallback((content: string) => {
    if (!target) return;
    publish(`/app/dms/${target.recipientUuid}/messages`, { content });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [publish, target?.recipientUuid]);

  return { messages, loading, send };
}
