import { useCallback, useEffect, useMemo, useState } from 'react';
import { Box, Button, IconButton, Paper, Stack, Tooltip, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import ContentPasteIcon from '@mui/icons-material/ContentPasteOutlined';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import DownloadIcon from '@mui/icons-material/DownloadOutlined';
import PlayArrowIcon from '@mui/icons-material/PlayArrowOutlined';
import OpenInFullIcon from '@mui/icons-material/OpenInFullOutlined';
import CloseFullscreenIcon from '@mui/icons-material/CloseFullscreenOutlined';
import CodeMirror from '@uiw/react-codemirror';
import SubmitButton from '@shared/components/SubmitButton';
import { useNotification } from '@shared/contexts/NotificationContext';
import { devUtilsApi } from '../api/devUtilsApi';
import { DiffLine, TextDiffResponse } from '../types';
import { downloadTextFile } from '../utils/downloadTextFile';
import { parseTextDiffInput, serializeTextDiffInput } from '../utils/textDiffInputFormat';
import { editorChromeTheme } from '../config/codeMirrorConfig';
import { GROWABLE_PANEL_MAX_HEIGHT } from '../config/panelSizing';
import { useResizableSplit } from '../hooks/useResizableSplit';
import { usePanelMaximize } from '../hooks/usePanelMaximize';
import PanelHeader from './PanelHeader';
import PanelResizeHandle from './PanelResizeHandle';
import { useCopyFeedback } from '../hooks/useCopyFeedback';

/** GitHub-style old/new line-number gutter widths for the Diff panel below — wide enough for a
 * comfortable 4-digit line count (this operation caps input at 2000 lines each side, see
 * `dev-utils-service/CLAUDE.md`'s own `TextDiffOperation` note) without wasting space on a typical
 * short diff. */
const DIFF_LINE_NUMBER_WIDTH = '2.75em';

interface DiffLineRow {
  line: DiffLine;
  /** Position in the Original text, or `null` for a line that only exists in Updated (`ADDED`). */
  oldLineNo: number | null;
  /** Position in the Updated text, or `null` for a line that only exists in Original (`REMOVED`). */
  newLineNo: number | null;
}

/** Derives GitHub-style old/new line numbers for each diff line — the backend's own
 * `TextDiffResponse.lines` carries no line-number field at all (see that DTO's own Javadoc; it's
 * deliberately just `type`/`text`), so this walks the sequence once, advancing the Original
 * counter on every `REMOVED`/`CONTEXT` line and the Updated counter on every `ADDED`/`CONTEXT`
 * line — exactly mirroring how `TextDiffOperation`'s own LCS reconstruction walked the two input
 * arrays to produce this same sequence in the first place. */
function buildDiffLineRows(lines: DiffLine[]): DiffLineRow[] {
  let oldLineNo = 0;
  let newLineNo = 0;
  return lines.map(line => {
    if (line.type === 'REMOVED') {
      oldLineNo += 1;
      return { line, oldLineNo, newLineNo: null };
    }
    if (line.type === 'ADDED') {
      newLineNo += 1;
      return { line, oldLineNo: null, newLineNo };
    }
    oldLineNo += 1;
    newLineNo += 1;
    return { line, oldLineNo, newLineNo };
  });
}

interface TextDiffPanelProps {
  /** Controlled — same lifted `input` state `DevUtilsPage.tsx` already threads into every other
   * panel, so this operation's headline row (Sample/Clear) keeps working unchanged. `input`
   * doubles as the serialized form of the `original`/`updated` pair (see
   * `utils/textDiffInputFormat.ts`'s own doc comment) — there is no separate local state for
   * either text block; both are derived from `input` on every render and write back through
   * `onInputChange`. */
  input: string;
  onInputChange: (value: string) => void;
  actionLabel: string;
  downloadFileName: string;
  /** Height (px) computed by `DevUtilsPage.tsx` from the actual viewport — the same value the
   * shared `DevUtilToolPanel`'s Input card and the sidebar both size themselves to (see that
   * component's own doc comment for how). Applied to the whole left column (Original + Updated
   * together, as a fixed `height`, each editor sharing half via `flex: 1`) and as a `minHeight`
   * floor on the Diff card — mirroring `DevUtilToolPanel.tsx`'s own Input-fixed/Output-floor split,
   * since a diffed file can genuinely be as large as this operation's own 2000-line cap on each
   * side (see `dev-utils-service/CLAUDE.md`'s `TextDiffOperation` note), not the small, fixed-size
   * result `HashGeneratorPanel.tsx`/`Base64ImagePanel.tsx` each produce. */
  availableHeight: number;
}

/** GitHub-style subtle backgrounds for added/removed lines — theme tokens, not fixed literals,
 * since (unlike `DevUtilToolPanel.tsx`'s own Output panel) this card's background otherwise just
 * follows the app's own light/dark theme, the same "plain theme tokens are the more correct
 * choice here" reasoning `RegExpTesterPanel.tsx`'s own error box already establishes. */
const ADDED_BG = (theme: import('@mui/material').Theme) => alpha(theme.palette.success.main, 0.12);
const REMOVED_BG = (theme: import('@mui/material').Theme) => alpha(theme.palette.error.main, 0.12);

function formatUnifiedDiffText(lines: DiffLine[]): string {
  return lines
    .map(line => {
      const prefix = line.type === 'ADDED' ? '+ ' : line.type === 'REMOVED' ? '- ' : '  ';
      return prefix + line.text;
    })
    .join('\n');
}

/**
 * Text Diff Checker's own bespoke Input/Output layout — a dedicated component, not a mode grafted
 * onto the shared `DevUtilToolPanel.tsx`, per direct request: this operation's input is genuinely
 * 2 separate text blocks (original/updated), and its output is a real line-by-line structure
 * (added/removed/unchanged), neither of which fits that component's single-code-editor
 * Input/Output pair at all — the same "some operations need a genuinely different layout"
 * precedent `HashGeneratorPanel.tsx`/`Base64ImagePanel.tsx`/`RegExpTesterPanel.tsx` already
 * established. `DevUtilsPage.tsx` renders this in place of `DevUtilToolPanel` for exactly this one
 * operation.
 *
 * <p>Two Input cards, the same shape `RegExpTesterPanel.tsx` already uses for its own two-box
 * Input side: **Original** and **Updated**, both writing into the same lifted `input` string via
 * `utils/textDiffInputFormat.ts`'s serialize/parse pair (see this file's own line-numbers
 * paragraph below for why each is a real CodeMirror editor, not a plain `TextField`).
 *
 * <p><b>No `onSubmit` prop, unlike every other operation's panel</b> — this operation's response
 * (`TextDiffResponse`) doesn't fit the shared `(input, minify) => Promise&lt;DevUtilsResponse&gt;`
 * signature `OperationConfig.onSubmit` establishes for every other operation (its own `lines`
 * array, with each line's real `ADDED`/`REMOVED`/`CONTEXT` type, would have to be formatted into a
 * string and re-parsed back apart for no reason — a real loss of information for line-by-line
 * data, unlike `HashGeneratorPanel.tsx`'s own label/value pairs, which round-trip through a plain
 * string cleanly). This component calls `devUtilsApi.compareTextDiff` directly instead, and
 * `config/operations.tsx`'s own `text-diff-checker` entry omits `onSubmit` entirely — the same
 * "omitted, not a dead placeholder" treatment `base64-image`'s own entry already establishes for
 * the same underlying reason (a genuinely different response shape), even though that operation
 * omits it for having no backend call at all rather than an incompatible response shape.
 *
 * <p>The **Diff** output card renders each line with real color — a subtle green background for
 * `ADDED`, red for `REMOVED`, plain for `CONTEXT` — mirroring GitHub's own diff view (the most
 * recognizable "this is a diff" visual convention most developers already know), not a plain
 * `-`/`+`-prefixed text block. Copy/Download both still produce real git-diff-style text
 * (`formatUnifiedDiffText`) — a `-`/`+`/two-space prefix per line — so the copied/downloaded
 * result is still directly usable as a plain-text diff outside this page.
 *
 * <p>**All three panels show line numbers, per request.** Original/Updated are real CodeMirror 6
 * editors — line numbers come for free from CodeMirror's own default `basicSetup`. The Diff panel
 * gets a GitHub-style **two-column** old/new gutter instead of one running count, since an
 * `ADDED`/`REMOVED` line only ever exists on one side — `buildDiffLineRows` derives both numbers
 * from the response's own `type` sequence, since `TextDiffResponse` carries no line-number field
 * itself.
 *
 * <p>**A diffed file can genuinely be as large as this operation's own 2000-line cap on each
 * side** (`dev-utils-service/CLAUDE.md`'s own `TextDiffOperation` note), unlike
 * `HashGeneratorPanel.tsx`'s/`Base64ImagePanel.tsx`'s own small, fixed-size results — so this
 * panel was brought up to the same viewport-relative sizing/resize/maximize treatment
 * `DevUtilToolPanel.tsx` already gives its own Input/Output. Original/Updated each fill half of
 * the left column's own fixed `availableHeight` (`flex: 1` each, both CodeMirror instances using
 * the same `position: 'relative'` wrapper + CodeMirror's own `position: 'absolute', inset: 0`
 * trick that component's Input editor already relies on); Diff floors at `availableHeight` and
 * grows past it, capped by `config/panelSizing.ts`'s own shared `GROWABLE_PANEL_MAX_HEIGHT`
 * (replacing the old fixed `minHeight: 240`/`maxHeight: 600` this card used before, both arbitrary
 * pixel numbers unrelated to the viewport). The resizable divider and maximize toggles both reuse
 * `hooks/useResizableSplit.ts`/`hooks/usePanelMaximize.ts`/`components/PanelResizeHandle.tsx` —
 * the same extraction `RegExpTesterPanel.tsx` also reuses, pulled out of
 * `DevUtilToolPanel.tsx`'s own original inline implementation. This panel's own split persists
 * under its own `localStorage` key, independent of the other two panels' ratios.
 */
export default function TextDiffPanel({
  input,
  onInputChange,
  actionLabel,
  downloadFileName,
  availableHeight,
}: TextDiffPanelProps): JSX.Element {
  const { showError } = useNotification();
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<TextDiffResponse | null>(null);
  const { copiedKey, copy } = useCopyFeedback();

  const { original, updated } = parseTextDiffInput(input);

  const { maximizedPanel, toggle: toggleMaximize } = usePanelMaximize<'left' | 'right'>();
  const toggleMaximizeLeft = useCallback(() => toggleMaximize('left'), [toggleMaximize]);
  const toggleMaximizeRight = useCallback(() => toggleMaximize('right'), [toggleMaximize]);

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
  } = useResizableSplit({ storageKey: 'devUtilsTextDiffPanelSplitPercent' });

  const updateField = useCallback(
    (field: 'original' | 'updated', value: string) => {
      onInputChange(serializeTextDiffInput({ original, updated, [field]: value }));
    },
    [onInputChange, original, updated]
  );

  // Mirrors DevUtilsPage.tsx's own Clear behavior for every other panel (blanking `input` also
  // blanks its own local output) — this panel's "output" can't be lifted into that page's plain
  // `string | null` state (see this component's own doc comment for why), so it clears its own
  // local copy whenever Clear resets the lifted `input` back to `''`.
  useEffect(() => {
    if (input === '') {
      setResult(null);
    }
  }, [input]);

  const handleCompare = useCallback(async () => {
    setSaving(true);
    try {
      const diff = await devUtilsApi.compareTextDiff(original, updated);
      setResult(diff);
    } catch (submitError) {
      setResult(null);
      const message = submitError instanceof Error ? submitError.message : 'Could not compare this text.';
      showError(message);
    } finally {
      setSaving(false);
    }
  }, [original, updated, showError]);

  const handlePaste = useCallback(
    async (field: 'original' | 'updated') => {
      try {
        const text = await navigator.clipboard.readText();
        updateField(field, text);
      } catch {
        showError('Could not read from the clipboard — check your browser permissions.');
      }
    },
    [updateField, showError]
  );

  // Recomputed only when a new comparison result actually lands, not on every keystroke in
  // Original/Updated (those don't touch `result` at all until Compare is clicked again).
  const diffRows = useMemo(() => (result ? buildDiffLineRows(result.lines) : []), [result]);

  const unifiedText = result ? formatUnifiedDiffText(result.lines) : null;

  const handleCopy = useCallback(() => {
    if (unifiedText === null) return;
    copy(unifiedText, 'output');
  }, [unifiedText, copy]);

  const handleDownload = useCallback(() => {
    if (unifiedText === null) return;
    downloadTextFile(downloadFileName, unifiedText);
  }, [unifiedText, downloadFileName]);

  return (
    // `position: 'relative'` anchors PanelResizeHandle; no `gap` between the two sides — both
    // columns' own flex-basis percentages sum to 100%, mirroring DevUtilToolPanel.tsx's row.
    <Box ref={rowRef} sx={{ position: 'relative', display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start' }}>
      <Box
        sx={{
          flex: maximizedPanel === 'left' ? '1 1 100%' : `1 1 ${splitPercent}%`,
          minWidth: 320,
          height: availableHeight,
          display: maximizedPanel === 'right' ? 'none' : 'flex',
          flexDirection: 'column',
          gap: 2,
          overflow: 'hidden',
        }}
      >
        <Paper variant="outlined" sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          <PanelHeader title="Original">
            <Button size="small" variant="outlined" startIcon={<ContentPasteIcon fontSize="small" />} onClick={() => handlePaste('original')}>
              Paste
            </Button>
          </PanelHeader>
          {/* `position: 'relative'` + CodeMirror's own `position: 'absolute', inset: 0` (via
              `style`) — the same trick DevUtilToolPanel.tsx's Input editor already relies on to
              fill an ancestor whose own size was arrived at through flex layout. */}
          <Box sx={{ position: 'relative', flex: 1, minHeight: 0 }}>
            <CodeMirror
              value={original}
              onChange={value => updateField('original', value)}
              placeholder={"const ship = () => 'today';\nconsole.log(ship());"}
              theme="light"
              extensions={[editorChromeTheme]}
              height="100%"
              style={{ position: 'absolute', inset: 0 }}
            />
          </Box>
        </Paper>

        <Paper variant="outlined" sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          <PanelHeader title="Updated">
            <Button size="small" variant="outlined" startIcon={<ContentPasteIcon fontSize="small" />} onClick={() => handlePaste('updated')}>
              Paste
            </Button>
            <SubmitButton
              size="small"
              saving={saving}
              label={actionLabel}
              startIcon={<PlayArrowIcon fontSize="small" />}
              onClick={handleCompare}
            />
            <Tooltip title={maximizedPanel === 'left' ? 'Restore split view' : 'Maximize Original/Updated'}>
              <IconButton size="small" onClick={toggleMaximizeLeft}>
                {maximizedPanel === 'left' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </PanelHeader>
          <Box sx={{ position: 'relative', flex: 1, minHeight: 0 }}>
            <CodeMirror
              value={updated}
              onChange={value => updateField('updated', value)}
              placeholder={"const ship = () => 'production';\nconsole.log(ship());\nconsole.log('Done 🚀');"}
              theme="light"
              extensions={[editorChromeTheme]}
              height="100%"
              style={{ position: 'absolute', inset: 0 }}
            />
          </Box>
        </Paper>
      </Box>

      <Paper
        variant="outlined"
        sx={{
          flex: maximizedPanel === 'right' ? '1 1 100%' : `1 1 ${100 - splitPercent}%`,
          minWidth: 320,
          minHeight: availableHeight,
          display: maximizedPanel === 'left' ? 'none' : 'flex',
          flexDirection: 'column',
        }}
      >
        <PanelHeader title="Diff">
          {result !== null && (
            <Stack direction="row" spacing={0.75} alignItems="center" sx={{ mr: 1 }}>
              <Typography variant="caption" fontWeight={700} color="success.main">
                +{result.addedCount}
              </Typography>
              <Typography variant="caption" fontWeight={700} color="error.main">
                −{result.removedCount}
              </Typography>
            </Stack>
          )}
          <Button
            size="small"
            variant="outlined"
            startIcon={<ContentCopyIcon fontSize="small" />}
            onClick={handleCopy}
            disabled={result === null}
          >
            {copiedKey === 'output' ? 'Copied!' : 'Copy'}
          </Button>
          <Tooltip title="Download">
            <span>
              <IconButton size="small" onClick={handleDownload} disabled={result === null}>
                <DownloadIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title={maximizedPanel === 'right' ? 'Restore split view' : 'Maximize Diff'}>
            <IconButton size="small" onClick={toggleMaximizeRight}>
              {maximizedPanel === 'right' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </PanelHeader>
        <Box sx={{ flex: 1, minHeight: 0, maxHeight: GROWABLE_PANEL_MAX_HEIGHT, overflow: 'auto' }}>
          {result === null ? (
            <Box sx={{ p: 2 }}>
              <Typography variant="body2" color="text.secondary">
                Diff results will appear here.
              </Typography>
            </Box>
          ) : result.lines.length === 0 ? (
            <Box sx={{ p: 2 }}>
              <Typography variant="body2" color="text.secondary">
                No differences — both texts are identical.
              </Typography>
            </Box>
          ) : (
            diffRows.map(({ line, oldLineNo, newLineNo }, index) => (
              <Box
                key={index}
                sx={{
                  py: 0.25,
                  display: 'flex',
                  fontFamily: 'monospace',
                  fontSize: '0.8rem',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  bgcolor: line.type === 'ADDED' ? ADDED_BG : line.type === 'REMOVED' ? REMOVED_BG : undefined,
                }}
              >
                <Box
                  component="span"
                  sx={{
                    flexShrink: 0,
                    width: DIFF_LINE_NUMBER_WIDTH,
                    textAlign: 'right',
                    pr: 0.75,
                    color: 'text.disabled',
                    userSelect: 'none',
                  }}
                >
                  {oldLineNo ?? ''}
                </Box>
                <Box
                  component="span"
                  sx={{
                    flexShrink: 0,
                    width: DIFF_LINE_NUMBER_WIDTH,
                    textAlign: 'right',
                    pr: 1,
                    borderRight: 1,
                    borderColor: 'divider',
                    color: 'text.disabled',
                    userSelect: 'none',
                  }}
                >
                  {newLineNo ?? ''}
                </Box>
                <Box
                  component="span"
                  sx={{
                    flexShrink: 0,
                    width: '1.5em',
                    textAlign: 'center',
                    color: line.type === 'ADDED' ? 'success.main' : line.type === 'REMOVED' ? 'error.main' : 'text.disabled',
                    fontWeight: 700,
                  }}
                >
                  {line.type === 'ADDED' ? '+' : line.type === 'REMOVED' ? '-' : ' '}
                </Box>
                <Box component="span" sx={{ pr: 2 }}>
                  {line.text}
                </Box>
              </Box>
            ))
          )}
        </Box>
      </Paper>

      <PanelResizeHandle
        ariaLabel="Resize Original/Updated and Diff panels"
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
    </Box>
  );
}
