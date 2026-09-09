import { useCallback, useState } from 'react';

// Long enough to register as a real acknowledgement, short enough that it doesn't linger past the
// user's next click — the exact duration every one of this feature's copy buttons already agreed
// on independently before this hook existed.
const COPIED_FEEDBACK_MS = 1500;

/**
 * Copies text to the clipboard and tracks a short-lived "Copied!" feedback flag — the
 * `navigator.clipboard.writeText` + `setTimeout`-reset pattern `DevUtilToolPanel.tsx`,
 * `HashGeneratorPanel.tsx`, and `Base64ImagePanel.tsx` each used to hand-roll independently, with
 * their own local `copied`/`copiedLabel`/`dataUrlCopied`/`previewCopied` state.
 *
 * <p>`key` distinguishes multiple independent copy targets sharing one hook instance —
 * `HashGeneratorPanel`'s 4 per-algorithm cards, or `Base64ImagePanel`'s Data-URL-box vs.
 * Preview-box buttons — so a component with several copy buttons needs only one call to this hook,
 * not one per button. `copiedKey` reports which target (if any) most recently finished copying; a
 * caller with a single copy target can just compare it against one fixed key (or omit `key`
 * entirely and compare against `'default'`).
 */
export function useCopyFeedback() {
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const copy = useCallback(async (text: string, key: string = 'default') => {
    await navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => {
      // Only clears *this* key's own feedback — a newer copy (a different key) may have already
      // taken over `copiedKey` by the time this fires, and this timeout must not stomp on it.
      setCopiedKey(prev => (prev === key ? null : prev));
    }, COPIED_FEEDBACK_MS);
  }, []);

  return { copiedKey, copy };
}
