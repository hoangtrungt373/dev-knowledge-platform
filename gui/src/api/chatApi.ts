import { STORAGE_KEYS } from '../constants/storage';
import { httpClient } from './httpClient';
import { ChatSessionSummary, SessionHistory, StreamCallbacks } from '../types/chat.types';

const BACKEND_BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8081';

type ShowError = (msg: string) => void;

/**
 * Chat API — wraps the three chat endpoints.
 *
 * streamChat uses fetch + ReadableStream instead of EventSource because
 * EventSource only supports GET and cannot send an Authorization header.
 * All SSE parsing happens here; callers receive typed callback invocations.
 */
export const chatApi = {

  listSessions(showError?: ShowError): Promise<ChatSessionSummary[]> {
    return httpClient.get('/api/v1/chat/sessions', showError);
  },

  getSessionHistory(sessionId: number, showError?: ShowError): Promise<SessionHistory> {
    return httpClient.get(`/api/v1/chat/sessions/${sessionId}`, showError);
  },

  /**
   * Opens an SSE stream to POST /api/v1/chat/stream and routes events to
   * the supplied callbacks.  The caller must pass an AbortSignal so the
   * stream can be cancelled when the component unmounts or the user presses Stop.
   *
   * SSE event sequence from the backend:
   *   session  → { sessionId: number }
   *   sources  → RagSource[]  (JSON)
   *   token    → plain text string (one LLM token at a time)
   *   done     → "[DONE]" (signals end of stream)
   */
  async streamChat(
    question: string,
    sessionId: number | null,
    callbacks: StreamCallbacks,
    signal: AbortSignal,
  ): Promise<void> {
    const token = localStorage.getItem(STORAGE_KEYS.accessToken);

    let response: Response;
    try {
      response = await fetch(`${BACKEND_BASE_URL}/api/v1/chat/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ question, sessionId: sessionId ?? undefined }),
        signal,
      });
    } catch (err) {
      if (signal.aborted) return;
      callbacks.onError(err instanceof Error ? err : new Error(String(err)));
      return;
    }

    if (!response.ok) {
      callbacks.onError(new Error(`Chat stream failed: ${response.status}`));
      return;
    }

    const reader = response.body!.getReader();
    const decoder = new TextDecoder('utf-8');

    let rawBuffer = '';
    let currentEvent = '';
    let currentDataLines: string[] = [];

    /**
     * Dispatches one fully-buffered SSE event to the appropriate callback.
     * Per the SSE spec, multiple `data:` lines are joined with LF.
     */
    const dispatchEvent = () => {
      if (currentDataLines.length === 0) return;
      const data = currentDataLines.join('\n');
      switch (currentEvent) {
        case 'session':
          callbacks.onSession(JSON.parse(data).sessionId);
          break;
        case 'sources':
          callbacks.onSources(JSON.parse(data));
          break;
        case 'token':
          // token data is plain text; no JSON.parse
          callbacks.onToken(data);
          break;
        case 'done':
          callbacks.onDone();
          break;
      }
      currentEvent = '';
      currentDataLines = [];
    };

    /**
     * Processes one logical SSE line.  Empty line = event boundary (dispatch).
     * The SSE spec strips exactly one leading space from the field value.
     */
    const processLine = (line: string) => {
      if (line === '') {
        dispatchEvent();
        return;
      }
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        const rest = line.slice(5); // keep everything after "data:"
        // Strip exactly one leading space per the SSE spec
        currentDataLines.push(rest.startsWith(' ') ? rest.slice(1) : rest);
      }
      // id: and retry: fields are intentionally ignored
    };

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        // Append decoded bytes; normalize CRLF/CR to LF before splitting
        rawBuffer += decoder.decode(value, { stream: true });
        const normalized = rawBuffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
        const lines = normalized.split('\n');

        // The last element may be an incomplete line — keep it in the buffer
        rawBuffer = lines.pop() ?? '';

        for (const line of lines) {
          processLine(line);
        }
      }

      // Flush any content that arrived without a trailing newline
      if (rawBuffer) {
        processLine(rawBuffer);
      }
      dispatchEvent();

    } catch (err) {
      if (signal.aborted) return;
      callbacks.onError(err instanceof Error ? err : new Error(String(err)));
    }
  },
};
