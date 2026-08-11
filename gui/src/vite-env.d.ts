/// <reference types="vite/client" />

/**
 * Vite env typing.
 *
 * - Vite exposes to client-side code ONLY environment variables prefixed with `VITE_`.
 * - These values are injected at dev-server start / build time.
 * - Two separate backend origins, not one — `gateway` is the single entry point for everything
 *   over plain HTTP, including SSE streaming chat now (`routing/ChatStreamProxyController` relays
 *   it by hand, since Spring Cloud Gateway Server MVC's usual RouterFunction routing has real,
 *   documented problems proxying Server-Sent Events). Only the WebSocket/STOMP connection still
 *   bypasses `gateway` — never routed at all, since Gateway Server MVC only proxies plain HTTP,
 *   not a protocol upgrade. See `@messaging/api/socket.ts`'s own comment on its constant.
 *
 * Example `.env.local`:
 *   VITE_BACKEND_URL=http://localhost:8080
 *   VITE_SOCIAL_SERVICE_URL=http://localhost:8084
 */
interface ImportMetaEnv {
  readonly VITE_BACKEND_URL?: string;
  readonly VITE_SOCIAL_SERVICE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

