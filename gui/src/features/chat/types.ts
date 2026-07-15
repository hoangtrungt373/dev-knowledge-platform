/** A single retrieved knowledge chunk shown as a source citation. */
export interface RagSource {
  contentItemId: number;
  sourceType: string;
  title: string;
  chunkText: string;
  similarity: number;
}

export type MessageRole = 'USER' | 'ASSISTANT';

/** A message held in local React state (not the server DTO). */
export interface LocalMessage {
  id: string;
  role: MessageRole;
  content: string;
  sources?: RagSource[];
  /** True while the assistant response is still streaming token-by-token. */
  streaming?: boolean;
}

export interface ChatSessionSummary {
  sessionId: number;
  title: string | null;
  lastActivityAt: string;
  messageCount: number;
}

export interface SessionHistory {
  sessionId: number;
  messages: Array<{
    role: string;
    content: string;
    turnIndex: number;
  }>;
}

/** Callbacks supplied to chatApi.streamChat; each maps to one SSE event type. */
export interface StreamCallbacks {
  onSession: (sessionId: number) => void;
  onSources: (sources: RagSource[]) => void;
  onToken: (token: string) => void;
  onDone: () => void;
  onError: (err: Error) => void;
}
