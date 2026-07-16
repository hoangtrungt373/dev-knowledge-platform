import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

const BACKEND_BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8081';

/** http(s):// → ws(s):// — same origin/port the REST API and SSE chat stream already use. */
function toBrokerUrl(baseUrl: string): string {
  return baseUrl.replace(/^http/, 'ws') + '/ws';
}

export type StompErrorHandler = (message: string) => void;

export interface ConnectOptions {
  onError?: StompErrorHandler;
  onConnectionChange?: (connected: boolean) => void;
}

/**
 * Thin facade over @stomp/stompjs's Client — the only place in this feature that touches the raw
 * library. No SockJS fallback: the backend registers a raw WebSocket endpoint only
 * (`registry.addEndpoint("/ws")`, no `.withSockJS()`), so this doesn't offer one either.
 *
 * The `Authorization` header is a STOMP CONNECT frame header, not an HTTP header — the WS handshake
 * itself is `permitAll` server-side (browsers can't set headers on it), so real auth happens on the
 * first STOMP frame instead (see StompAuthChannelInterceptor). `beforeConnect` re-reads the token via
 * `getToken` on every (re)connection attempt, including stompjs's own automatic reconnects, so a
 * post-refresh token is picked up without the caller having to manually reconnect.
 */
export class StompSocket {
  private client: Client | null = null;

  connect(getToken: () => string | null, options: ConnectOptions = {}): void {
    if (this.client?.active) return;

    const client = new Client({
      brokerURL: toBrokerUrl(BACKEND_BASE_URL),
      reconnectDelay: 5000,
      beforeConnect: () => {
        const token = getToken();
        client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      },
      onConnect: () => options.onConnectionChange?.(true),
      onDisconnect: () => options.onConnectionChange?.(false),
      onWebSocketClose: () => options.onConnectionChange?.(false),
      onStompError: frame => {
        options.onError?.(frame.headers['message'] ?? 'WebSocket protocol error');
      },
    });

    this.client = client;
    client.activate();
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = null;
  }

  get connected(): boolean {
    return this.client?.connected ?? false;
  }

  /** Publishes to an `/app/**` destination; `body` is JSON-stringified. No-ops if not connected. */
  publish(destination: string, body: unknown): void {
    this.client?.publish({ destination, body: JSON.stringify(body) });
  }

  /**
   * Subscribes to a `/topic/**` or `/user/queue/**` destination; `handler` receives the parsed JSON
   * body. Returns an unsubscribe function. A subscribe() call made before the first connect is
   * re-issued on every future reconnect too (subscriptions don't survive a dropped socket, so this
   * is required, not just a queueing convenience). A subscribe() call made after the client is
   * already connected is NOT re-issued on a later reconnect — acceptable for Phase 1 (reconnect
   * polish is a Phase 3 item); app-wide subscriptions (`/user/queue/dms`, `/user/queue/errors`) are
   * always registered before the first connect via StompConnectionContext, so this only affects a
   * per-thread subscription added mid-session across a reconnect.
   */
  subscribe<T>(destination: string, handler: (payload: T) => void): () => void {
    if (!this.client) return () => {};

    let sub: StompSubscription | null = null;
    const doSubscribe = () => {
      sub = this.client!.subscribe(destination, (message: IMessage) => {
        handler(JSON.parse(message.body) as T);
      });
    };

    if (this.client.connected) {
      doSubscribe();
    } else {
      const prevOnConnect = this.client.onConnect;
      this.client.onConnect = frame => {
        prevOnConnect?.(frame);
        doSubscribe();
      };
    }

    return () => sub?.unsubscribe();
  }
}

export const stompSocket = new StompSocket();
