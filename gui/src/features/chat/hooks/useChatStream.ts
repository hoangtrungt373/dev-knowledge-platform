import { useCallback, useRef, useState } from 'react';
import { chatApi } from '../api/chatApi';
import { LocalMessage, RagSource } from '../types';

function uid(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

export interface UseChatStreamReturn {
  messages: LocalMessage[];
  streaming: boolean;
  /** The active session ID, set by the backend on the first message. */
  sessionId: number | null;
  /** Send a question; uses the current sessionId so the conversation continues. */
  sendMessage: (question: string) => Promise<void>;
  /** Cancel an in-flight stream without removing the partial response. */
  abort: () => void;
  /** Load a prior session's history and make it the active session. */
  loadHistory: (sid: number) => Promise<void>;
  /** Clear all messages and session context (for "New chat"). */
  resetMessages: () => void;
}

/**
 * Manages the local message list and SSE streaming lifecycle for one chat session.
 *
 * Design choice — sessionId lives here rather than in the page component so that
 * the hook can include it in every subsequent sendMessage call without the caller
 * needing to thread it through.  The page observes the exported sessionId and
 * updates the URL via useNavigate (one-way data flow: hook → page → URL).
 */
export function useChatStream(): UseChatStreamReturn {
  const [messages, setMessages] = useState<LocalMessage[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const resetMessages = useCallback(() => {
    setMessages([]);
    setSessionId(null);
  }, []);

  const loadHistory = useCallback(async (sid: number) => {
    const history = await chatApi.getSessionHistory(sid);
    setSessionId(history.sessionId);
    setMessages(
      [...history.messages]
        .sort((a, b) => a.turnIndex - b.turnIndex)
        .map(m => ({
          id: uid(),
          role: m.role as 'USER' | 'ASSISTANT',
          content: m.content,
        })),
    );
  }, []);

  const sendMessage = useCallback(async (question: string) => {
    if (streaming) return;

    const userMsgId = uid();
    const assistantMsgId = uid();

    setMessages(prev => [
      ...prev,
      { id: userMsgId, role: 'USER', content: question },
      { id: assistantMsgId, role: 'ASSISTANT', content: '', streaming: true },
    ]);
    setStreaming(true);

    const ctrl = new AbortController();
    abortRef.current = ctrl;

    // Capture current sessionId for this request; the state value in the
    // closure reflects the session at the time sendMessage was called.
    const currentSessionId = sessionId;

    await chatApi.streamChat(
      question,
      currentSessionId,
      {
        onSession(sid: number) {
          setSessionId(sid);
        },
        onSources(sources: RagSource[]) {
          setMessages(prev =>
            prev.map(m => (m.id === assistantMsgId ? { ...m, sources } : m)),
          );
        },
        onToken(token: string) {
          setMessages(prev =>
            prev.map(m =>
              m.id === assistantMsgId
                ? { ...m, content: m.content + token }
                : m,
            ),
          );
        },
        onDone() {
          setMessages(prev =>
            prev.map(m =>
              m.id === assistantMsgId ? { ...m, streaming: false } : m,
            ),
          );
          setStreaming(false);
        },
        onError() {
          setMessages(prev =>
            prev.map(m =>
              m.id === assistantMsgId
                ? { ...m, content: 'Something went wrong. Please try again.', streaming: false }
                : m,
            ),
          );
          setStreaming(false);
        },
      },
      ctrl.signal,
    );
  // sessionId is intentionally read via ref-like closure; it is the value at
  // call time and should not cause the callback to be recreated on each render.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [streaming, sessionId]);

  const abort = useCallback(() => {
    abortRef.current?.abort();
    setStreaming(false);
    setMessages(prev =>
      prev.map(m => (m.streaming ? { ...m, streaming: false } : m)),
    );
  }, []);

  return { messages, streaming, sessionId, sendMessage, abort, loadHistory, resetMessages };
}
