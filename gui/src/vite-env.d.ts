/// <reference types="vite/client" />

/**
 * Vite env typing.
 *
 * - Vite exposes to client-side code ONLY environment variables prefixed with `VITE_`.
 * - These values are injected at dev-server start / build time.
 *
 * Example `.env.local`:
 *   VITE_BACKEND_URL=http://localhost:8081
 */
interface ImportMetaEnv {
  readonly VITE_BACKEND_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

