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
 * - `VITE_KEYCLOAK_URL`/`VITE_KEYCLOAK_REALM` are Keycloak's own origin/realm, used only by the
 *   admin login flow (`@auth/services/adminAuthService.ts`) to redirect the browser directly to
 *   Keycloak's hosted login/token/end-session endpoints — a public SPA client (see
 *   `docker/keycloak/realm-export.json`'s "gui" client) talks to Keycloak directly, never through
 *   `gateway`. Defaults match `gateway/application.yml`'s own `issuer-uri` default.
 *
 * Example `.env.local`:
 *   VITE_BACKEND_URL=http://localhost:8080
 *   VITE_SOCIAL_SERVICE_URL=http://localhost:8084
 *   VITE_KEYCLOAK_URL=http://localhost:8180
 *   VITE_KEYCLOAK_REALM=dev-knowledge-platform
 */
interface ImportMetaEnv {
  readonly VITE_BACKEND_URL?: string;
  readonly VITE_SOCIAL_SERVICE_URL?: string;
  readonly VITE_KEYCLOAK_URL?: string;
  readonly VITE_KEYCLOAK_REALM?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

