import { useCallback, useEffect, useState } from 'react';
import { useNotification } from '@shared/contexts/NotificationContext';
import { dmApi } from '../api/dmApi';
import { useStompConnection } from '../context/StompConnectionContext';
import { DmThread } from '../types';

/**
 * The authenticated user's DM conversation list — REST for the initial load, then kept live by
 * bumping/re-sorting on every incoming message. A message whose threadId isn't in the list yet
 * (a brand-new conversation) triggers a full refresh rather than fabricating a DmThread from the
 * message alone — a DmThread needs `otherUser`'s summary, which the message itself doesn't carry.
 */
export function useDmThreads(): { threads: DmThread[]; loading: boolean; refresh: () => void } {
  const { showError } = useNotification();
  const { onDmMessage } = useStompConnection();
  const [threads, setThreads] = useState<DmThread[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(() => {
    setLoading(true);
    dmApi.listMyThreads({ page: 0, size: 50 }, showError)
      .then(data => setThreads(data.content))
      .finally(() => setLoading(false));
  }, [showError]);

  useEffect(() => { refresh(); }, [refresh]);

  useEffect(() => {
    return onDmMessage(message => {
      setThreads(prev => {
        const existing = prev.find(t => t.id === message.threadId);
        if (!existing) {
          refresh();
          return prev;
        }
        const updated = { ...existing, lastMessageAt: message.createdAt };
        return [updated, ...prev.filter(t => t.id !== message.threadId)];
      });
    });
  }, [onDmMessage, refresh]);

  return { threads, loading, refresh };
}
