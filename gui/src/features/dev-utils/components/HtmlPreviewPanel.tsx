import { useCallback, useState } from 'react';
import { Box, Button, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import ContentPasteIcon from '@mui/icons-material/ContentPasteOutlined';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import CheckIcon from '@mui/icons-material/CheckOutlined';
import DownloadIcon from '@mui/icons-material/DownloadOutlined';
import PlayArrowIcon from '@mui/icons-material/PlayArrowOutlined';
import OpenInFullIcon from '@mui/icons-material/OpenInFullOutlined';
import CloseFullscreenIcon from '@mui/icons-material/CloseFullscreenOutlined';
import CodeMirror from '@uiw/react-codemirror';
import SubmitButton from '@shared/components/SubmitButton';
import { useNotification } from '@shared/contexts/NotificationContext';
import { DevUtilsResponse } from '../types';
import { downloadTextFile } from '../utils/downloadTextFile';
import { editorChromeTheme, getCodeMirrorExtensions } from '../config/codeMirrorConfig';
import { useResizableSplit } from '../hooks/useResizableSplit';
import { usePanelMaximize } from '../hooks/usePanelMaximize';
import PanelHeader from './PanelHeader';
import PanelResizeHandle from './PanelResizeHandle';
import { useCopyFeedback } from '../hooks/useCopyFeedback';

interface HtmlPreviewPanelProps {
  /** Controlled — same lifted `input` state `DevUtilsPage.tsx` already threads into
   * `DevUtilToolPanel` for every other operation, so this operation's headline row (Sample/Clear)
   * keeps working unchanged even though the rest of this panel's own layout doesn't. */
  input: string;
  onInputChange: (value: string) => void;
  inputPlaceholder: string;
  actionLabel: string;
  downloadFileName: string;
  /** Same `Promise<DevUtilsResponse>` contract every operation's `onSubmit` returns — `output` is
   * `HtmlPreviewOperation`'s own sanitized HTML, handed to the preview iframe as `srcdoc`. */
  onSubmit: (input: string, minify: boolean) => Promise<DevUtilsResponse>;
  /** Height (px) computed by `DevUtilsPage.tsx` from the actual viewport — the same value the
   * shared `DevUtilToolPanel`'s Input card and the sidebar both size themselves to. Applied as a
   * fixed `height` to **both** cards here, not a floor — a rendered preview is a bounded viewport
   * onto a page, the same "one bounded thing, never needs to grow past this" reasoning
   * `Base64ImagePanel.tsx`'s own Preview card already establishes for a bounded image, rather than
   * `RegExpTesterPanel.tsx`'s/`TextDiffPanel.tsx`'s own free-form, potentially-huge text results. */
  availableHeight: number;
}

/**
 * HTML Preview's own bespoke Input/Output layout — a dedicated component, not a mode grafted onto
 * the shared `DevUtilToolPanel.tsx`, per direct request: this operation's Output is a live,
 * rendered page (a sandboxed iframe), not a syntax-highlighted text block, which doesn't fit that
 * component's plain-text Output design at all — the same "some operations need a genuinely
 * different layout" precedent `HashGeneratorPanel.tsx`/`Base64ImagePanel.tsx`/
 * `RegExpTesterPanel.tsx`/`TextDiffPanel.tsx` already established. `DevUtilsPage.tsx` renders this
 * in place of `DevUtilToolPanel` for exactly this one operation.
 *
 * <p>**Input** is a real CodeMirror 6 editor (HTML highlighting, same `getCodeMirrorExtensions`
 * every operation's own Input editor already uses) with Paste and the "Preview" action in its own
 * header. **Output** is a sandboxed `<iframe srcdoc={...}>` rendering `HtmlPreviewOperation`'s own
 * sanitized markup — deliberately **no `allow-scripts` permission at all** (per direct request), so
 * any `<script>` that somehow survived server-side sanitization still could not execute; this is
 * the client-side half of the same defense-in-depth the backend operation's own Javadoc describes,
 * not a substitute for it. The iframe's own background is a fixed white (`#ffffff`, the same
 * "always looks like a real page, independent of the app's own light/dark theme" reasoning
 * `HashGeneratorPanel.tsx`'s own result cards already establish) — most plain HTML snippets assume
 * a light page background, and a dark app theme showing an unstyled snippet through a dark
 * background would misrepresent what the sanitized markup actually looks like.
 *
 * <p>Toast-only error handling on a failed submit (not the inline Output-panel error
 * `RegExpTesterPanel.tsx` renders) — `HtmlPreviewOperation` never throws server-side (jsoup's
 * `Cleaner` always produces a best-effort sanitized document, the same lenient-parser shape
 * `HtmlBeautifyOperation` already establishes), so the only realistic failure here is a network/
 * technical error, the same rare-edge-case reasoning `HashGeneratorPanel.tsx`'s own toast-only
 * handling already documents.
 *
 * <p>Same resizable-split-plus-maximize mechanism every custom panel in this feature now shares —
 * a single horizontal split (`hooks/useResizableSplit.ts`'s default orientation — this operation
 * only ever has 2 cards, not 3, so there's no second stacked card to divide) plus a 2-way
 * `usePanelMaximize<'input' | 'output'>()` toggle, the same 2-panel shape `DevUtilToolPanel.tsx`/
 * `HashGeneratorPanel.tsx` themselves use.
 */
export default function HtmlPreviewPanel({
  input,
  onInputChange,
  inputPlaceholder,
  actionLabel,
  downloadFileName,
  onSubmit,
  availableHeight,
}: HtmlPreviewPanelProps): JSX.Element {
  const { showError } = useNotification();
  const [saving, setSaving] = useState(false);
  const [output, setOutput] = useState<string | null>(null);
  const { copiedKey, copy } = useCopyFeedback();

  const { maximizedPanel, toggle: toggleMaximize } = usePanelMaximize<'input' | 'output'>();
  const toggleMaximizeInput = useCallback(() => toggleMaximize('input'), [toggleMaximize]);
  const toggleMaximizeOutput = useCallback(() => toggleMaximize('output'), [toggleMaximize]);

  const {
    rowRef,
    splitPercent,
    resizing,
    minPercent,
    maxPercent,
    handlePointerDown,
    handlePointerMove,
    handlePointerUp,
    handleDoubleClick,
    handleKeyDown,
  } = useResizableSplit({ storageKey: 'devUtilsHtmlPreviewPanelSplitPercent' });

  const handlePreview = useCallback(async () => {
    setSaving(true);
    try {
      const result = await onSubmit(input, false);
      setOutput(result.output);
    } catch (submitError) {
      setOutput(null);
      const message = submitError instanceof Error ? submitError.message : 'Could not render this preview.';
      showError(message);
    } finally {
      setSaving(false);
    }
  }, [input, onSubmit, showError]);

  const handlePaste = useCallback(async () => {
    try {
      const text = await navigator.clipboard.readText();
      onInputChange(text);
    } catch {
      showError('Could not read from the clipboard — check your browser permissions.');
    }
  }, [onInputChange, showError]);

  const handleCopy = useCallback(() => {
    if (output === null) return;
    copy(output, 'output');
  }, [output, copy]);

  const handleDownload = useCallback(() => {
    if (output === null) return;
    downloadTextFile(downloadFileName, output);
  }, [output, downloadFileName]);

  const inputHidden = maximizedPanel === 'output';
  const outputHidden = maximizedPanel === 'input';

  return (
    // `position: 'relative'` anchors PanelResizeHandle; no `gap` — both sides' own flex-basis
    // percentages sum to 100%, mirroring every other resizable split in this feature.
    <Box ref={rowRef} sx={{ position: 'relative', display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start' }}>
      <Paper
        variant="outlined"
        sx={{
          flex: maximizedPanel === 'input' ? '1 1 100%' : `1 1 ${splitPercent}%`,
          minWidth: 320,
          height: availableHeight,
          display: inputHidden ? 'none' : 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        <PanelHeader title="Input">
          <Button size="small" variant="outlined" startIcon={<ContentPasteIcon fontSize="small" />} onClick={handlePaste}>
            Paste
          </Button>
          <SubmitButton
            size="small"
            saving={saving}
            label={actionLabel}
            startIcon={<PlayArrowIcon fontSize="small" />}
            onClick={handlePreview}
            disabled={!input.trim()}
          />
          <Tooltip title={maximizedPanel === 'input' ? 'Restore split view' : 'Maximize Input'}>
            <IconButton size="small" onClick={toggleMaximizeInput}>
              {maximizedPanel === 'input' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </PanelHeader>
        {/* `position: 'relative'` + CodeMirror's own `position: 'absolute', inset: 0` (via
            `style`) — the same trick DevUtilToolPanel.tsx's Input editor already relies on to fill
            an ancestor whose own size comes from flex layout rather than a plain CSS percentage
            height. */}
        <Box sx={{ position: 'relative', flex: 1, minHeight: 0 }}>
          <CodeMirror
            value={input}
            onChange={onInputChange}
            placeholder={inputPlaceholder}
            theme="light"
            extensions={[editorChromeTheme, ...getCodeMirrorExtensions('html')]}
            height="100%"
            style={{ position: 'absolute', inset: 0 }}
          />
        </Box>
      </Paper>

      <PanelResizeHandle
        ariaLabel="Resize Input/Output panels"
        splitPercent={splitPercent}
        minPercent={minPercent}
        maxPercent={maxPercent}
        resizing={resizing}
        hidden={maximizedPanel !== null}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onDoubleClick={handleDoubleClick}
        onKeyDown={handleKeyDown}
      />

      <Paper
        variant="outlined"
        sx={{
          flex: maximizedPanel === 'output' ? '1 1 100%' : `1 1 ${100 - splitPercent}%`,
          minWidth: 320,
          height: availableHeight,
          display: outputHidden ? 'none' : 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        <PanelHeader title="Preview" actionsSpacing={0.5}>
          <Tooltip title={copiedKey === 'output' ? 'Copied!' : 'Copy sanitized HTML'}>
            <span>
              <IconButton size="small" onClick={handleCopy} disabled={output === null}>
                {copiedKey === 'output' ? <CheckIcon fontSize="small" color="success" /> : <ContentCopyIcon fontSize="small" />}
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title="Download">
            <span>
              <IconButton size="small" onClick={handleDownload} disabled={output === null}>
                <DownloadIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title={maximizedPanel === 'output' ? 'Restore split view' : 'Maximize Preview'}>
            <IconButton size="small" onClick={toggleMaximizeOutput}>
              {maximizedPanel === 'output' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </PanelHeader>
        {output === null ? (
          <Box sx={{ flex: 1, minHeight: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: '#ffffff' }}>
            <Typography variant="body2" color="text.secondary">
              Preview will appear here.
            </Typography>
          </Box>
        ) : (
          // No `allow-scripts` in `sandbox` — deliberately, per direct request (see this
          // component's own doc comment): even if a <script> tag somehow survived server-side
          // sanitization, this iframe still could not execute it. `srcdoc`, not a `src` blob URL —
          // the sanitized HTML never needs its own same-origin/storage access, only rendering.
          <Box
            component="iframe"
            title="HTML preview"
            srcDoc={output}
            sandbox=""
            sx={{ flex: 1, minHeight: 0, border: 'none', bgcolor: '#ffffff' }}
          />
        )}
      </Paper>
    </Box>
  );
}
