import { useCallback } from 'react';
import { useNotification } from '@shared/contexts/NotificationContext';
import { downloadTextFile } from '../utils/downloadTextFile';
import { useCopyFeedback } from './useCopyFeedback';

/**
 * The "Copy the whole Output block, or download it as a file" handler pair every panel with a
 * single-string result needs — `DevUtilToolPanel.tsx`/`RegExpTesterPanel.tsx`/
 * `HtmlPreviewPanel.tsx`/`LoremIpsumPanel.tsx`/`TextDiffPanel.tsx` (whose own "output" is really
 * `formatUnifiedDiffText(result.lines)`, not the raw `TextDiffResponse` — still a single string by
 * the time it reaches here) each hand-rolled this exact `if (output === null) return; copy(...)`/
 * `downloadTextFile(...)` pair independently, always keyed `'output'` for the copy-feedback state.
 * Wraps `useCopyFeedback` rather than requiring a separate call at each site, and always shows a
 * "Downloaded ..." toast on a successful download — `DevUtilToolPanel.tsx` was the only one of
 * these five that used to do this; normalized here so every custom panel's Download button behaves
 * identically rather than only one of them confirming the download happened.
 *
 * <p>Not used by `HashGeneratorPanel.tsx`/`ColorConverterPanel.tsx`/`Base64ImagePanel.tsx`/
 * `QrCodePanel.tsx` — each of those has its own multi-target copy shape (one Copy button per
 * result card/field, or two independent Data-URL/Preview copy targets) that doesn't fit a single
 * "the output" string at all.
 */
export function useOutputActions(output: string | null, downloadFileName: string) {
  const { showSuccess } = useNotification();
  const { copiedKey, copy } = useCopyFeedback();

  const handleCopy = useCallback(() => {
    if (output === null) return;
    copy(output, 'output');
  }, [output, copy]);

  const handleDownload = useCallback(() => {
    if (output === null) return;
    downloadTextFile(downloadFileName, output);
    showSuccess(`Downloaded ${downloadFileName}`);
  }, [output, downloadFileName, showSuccess]);

  return { copiedKey, handleCopy, handleDownload };
}
