import { useCallback } from 'react';
import { useNotification } from '@shared/contexts/NotificationContext';

/**
 * The "read plain text off the clipboard, hand it to a callback, toast on failure" handler every
 * Paste button in this feature needs — `DevUtilToolPanel.tsx`/`HashGeneratorPanel.tsx`/
 * `ColorConverterPanel.tsx`/`HtmlPreviewPanel.tsx` each hand-rolled this exact
 * `navigator.clipboard.readText()` + try/catch independently; `RegExpTesterPanel.tsx`/
 * `TextDiffPanel.tsx` each hand-rolled a field-specific variant of the same thing. One hook covers
 * both shapes: pass `onInputChange` directly for a panel with a single `input` string, or a small
 * wrapper (`text => updateField('testText', text)`) for a panel whose Paste button targets one of
 * several fields.
 *
 * <p>Does **not** cover an image-capable paste (Base64 Image's/QR Code's own upload cards, which
 * also accept a pasted screenshot) — see `utils/clipboardImage.ts` for that separate concern; this
 * hook is for the plain-text-only case every other panel's Paste button actually needs.
 */
export function usePasteText(onPaste: (text: string) => void) {
  const { showError } = useNotification();
  return useCallback(async () => {
    try {
      const text = await navigator.clipboard.readText();
      onPaste(text);
    } catch {
      showError('Could not read from the clipboard — check your browser permissions.');
    }
  }, [onPaste, showError]);
}
