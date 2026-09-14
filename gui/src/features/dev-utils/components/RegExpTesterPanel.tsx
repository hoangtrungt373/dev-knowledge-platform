import { useCallback, useState } from 'react';
import { Box, Button, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import ContentPasteIcon from '@mui/icons-material/ContentPasteOutlined';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import DownloadIcon from '@mui/icons-material/DownloadOutlined';
import PlayArrowIcon from '@mui/icons-material/PlayArrowOutlined';
import CodeMirror from '@uiw/react-codemirror';
import SubmitButton from '@shared/components/SubmitButton';
import { DevUtilsResponse } from '../types';
import { DevUtilError } from '../utils/errorFormatting';
import { parseRegexInput, SAMPLE_EMAIL_TEST_TEXT, serializeRegexInput } from '../utils/regexInputFormat';
import { editorChromeTheme } from '../config/codeMirrorConfig';
import { GROWABLE_PANEL_MAX_HEIGHT } from '../config/panelSizing';
import { useResizableSplit } from '../hooks/useResizableSplit';
import { usePanelMaximize } from '../hooks/usePanelMaximize';
import { usePasteText } from '../hooks/usePasteText';
import { useOutputActions } from '../hooks/useOutputActions';
import PanelHeader from './PanelHeader';
import PanelResizeHandle from './PanelResizeHandle';
import MaximizeToggleButton from './MaximizeToggleButton';
import InlineErrorBox from './InlineErrorBox';

interface RegExpTesterPanelProps {
  /** Controlled — same lifted `input`/`output`/`error` state `DevUtilsPage.tsx` already threads
   * into `DevUtilToolPanel` for every other operation, so this operation's headline row
   * (Sample/Clear) keeps working unchanged even though the rest of this panel's own layout
   * doesn't. `input` doubles as the serialized form of all 3 visual fields below (see
   * `utils/regexInputFormat.ts`'s own doc comment) — there is no separate local state for
   * pattern/flags/testText themselves; every field is derived from `input` on every render and
   * writes back through `onInputChange`. */
  input: string;
  onInputChange: (value: string) => void;
  output: string | null;
  onOutputChange: (value: string | null) => void;
  /** Unlike `HashGeneratorPanel.tsx`'s own toast-only error handling, a failed submit here renders
   * inline in the Output panel — the same treatment `DevUtilToolPanel.tsx` already established,
   * picked deliberately over a toast because an invalid-pattern error is a common, expected
   * outcome while actively typing a regex (unlike `HashGeneratorOperation`, which practically
   * never fails), not a rare edge case a toast's own disappearing act is an acceptable loss for. */
  error: DevUtilError | null;
  onErrorChange: (error: DevUtilError | null) => void;
  actionLabel: string;
  downloadFileName: string;
  onSubmit: (input: string, minify: boolean) => Promise<DevUtilsResponse>;
  /** Height (px) computed by `DevUtilsPage.tsx` from the actual viewport — the same value the
   * shared `DevUtilToolPanel`'s Input card and the sidebar both size themselves to (see that
   * component's own doc comment for how). Applied to the whole left column (Pattern + Test String
   * together, as a fixed `height`) and as a `minHeight` floor on the Output card, mirroring
   * `DevUtilToolPanel.tsx`'s own Input-fixed/Output-floor split exactly — see this component's own
   * doc comment for why a test string or a match list can each genuinely grow large enough to
   * need it, unlike `HashGeneratorPanel.tsx`'s own inherently small results. */
  availableHeight: number;
}

/**
 * RegExp Tester's own bespoke Input/Output layout — a dedicated component, not a mode grafted
 * onto the shared `DevUtilToolPanel.tsx`, per direct request: this operation's input is genuinely
 * 3 distinct fields (pattern, flags, test text), not one string with a minify flag, which doesn't
 * fit that component's single-code-editor Input design at all — the same "some operations need a
 * genuinely different layout" precedent `HashGeneratorPanel.tsx`/`Base64ImagePanel.tsx` already
 * established. `DevUtilsPage.tsx` renders this in place of `DevUtilToolPanel` for exactly this one
 * operation.
 *
 * <p>Two Input cards, per the same shape `Base64ImagePanel.tsx` already uses for its own two-box
 * Input side: **Pattern** (the regex + flags, on one row, styled like a familiar
 * {@code /pattern/flags} literal) and **Test String** (a real CodeMirror 6 editor — see this
 * file's own line-numbers/fill-height paragraph below for why, over the plain `TextField` this
 * card used before — with Paste and the actual Test action in its own header). Both write into
 * the same lifted `input` string via `utils/regexInputFormat.ts`'s serialize/parse pair, so
 * Sample/Clear (and a bookmarked/shared hash link) keep working exactly like every other
 * operation's — see that file's own doc comment for the exact format and why it's safe to
 * round-trip.
 *
 * <p>The **Output** card is a plain read-only text block (no CodeMirror — matches are a short
 * list of extracted strings, not code to syntax-highlight, the same "much simpler than the shared
 * panel" choice `HashGeneratorPanel.tsx` already makes), blank-line separated exactly as the
 * backend returns them (a match can itself contain a newline, so no separator at all would make
 * two matches indistinguishable from one). Copy/Download act on the whole block, mirroring the
 * shared panel's own Output header actions.
 *
 * <p>**A test string against a large body of text can produce a genuinely large match list** —
 * unlike `HashGeneratorPanel.tsx`'s own inherently small, fixed-size results, this operation's own
 * input/output sizes track `DevUtilToolPanel.tsx`'s own Input/Output more closely. This panel was
 * brought up to that same viewport-relative sizing: **Test String** is a real CodeMirror editor
 * (not the plain `TextField` it used before) so it can fill its own share of the left column's
 * fixed `availableHeight` — the exact same `position: 'relative'` + CodeMirror's own `position:
 * 'absolute', inset: 0` trick `DevUtilToolPanel.tsx`'s Input editor already relies on. **Output**
 * floors at `availableHeight` and grows past it for a long match list, capped by
 * `config/panelSizing.ts`'s own shared `GROWABLE_PANEL_MAX_HEIGHT`.
 *
 * <p>**All 3 panels can be resized/maximized, per a follow-up request extending
 * `Base64ImagePanel.tsx`'s own "same approach" to this operation.** Two independent splits, both
 * via `hooks/useResizableSplit.ts`: a **horizontal** one (default orientation) between the left
 * column and Output, and a **vertical** one (`orientation: 'vertical'`, defaulting 25%/75%)
 * between Pattern and Test String inside that column — Pattern only ever needs a small,
 * single-line-plus-caption amount of room, so it starts noticeably smaller than Test String, the
 * same "smaller side defaults smaller" reasoning `Base64ImagePanel.tsx`'s own Upload/Image Data
 * URL split already established. Both handles are the shared `components/PanelResizeHandle.tsx`,
 * each persisted under its own `localStorage` key.
 *
 * <p>`maximizedPanel` is a **3-way exclusive toggle** (`'pattern' | 'testString' | 'output'`, via
 * `hooks/usePanelMaximize.ts`) — not a 2-way "column vs. Output" toggle, since Pattern and Test
 * String are each independently maximizable now that they have their own resizable split.
 * Maximizing any one hides the *other two* entirely: maximizing Output hides the whole left
 * column (both handles hide too, nothing left to drag); maximizing Pattern or Test String hides
 * Output *and* the sibling card within the left column. Width-only, the same convention every
 * other panel's maximize already establishes.
 */
export default function RegExpTesterPanel({
  input,
  onInputChange,
  output,
  onOutputChange,
  error,
  onErrorChange,
  actionLabel,
  downloadFileName,
  onSubmit,
  availableHeight,
}: RegExpTesterPanelProps): JSX.Element {
  const [saving, setSaving] = useState(false);
  const { copiedKey, handleCopy, handleDownload } = useOutputActions(output, downloadFileName);

  const { pattern, flags, testText } = parseRegexInput(input);

  const { maximizedPanel, toggle: toggleMaximize } = usePanelMaximize<'pattern' | 'testString' | 'output'>();
  const toggleMaximizePattern = useCallback(() => toggleMaximize('pattern'), [toggleMaximize]);
  const toggleMaximizeTestString = useCallback(() => toggleMaximize('testString'), [toggleMaximize]);
  const toggleMaximizeOutput = useCallback(() => toggleMaximize('output'), [toggleMaximize]);

  // Horizontal: the left column (Pattern + Test String together) vs. Output.
  const {
    rowRef,
    splitPercent: horizontalSplit,
    resizing: horizontalResizing,
    minPercent: horizontalMin,
    maxPercent: horizontalMax,
    handlePointerDown: handleHorizontalPointerDown,
    handlePointerMove: handleHorizontalPointerMove,
    handlePointerUp: handleHorizontalPointerUp,
    handleDoubleClick: handleHorizontalDoubleClick,
    handleKeyDown: handleHorizontalKeyDown,
  } = useResizableSplit({ storageKey: 'devUtilsRegexPanelHorizontalSplitPercent' });

  // Vertical: Pattern vs. Test String, stacked inside the left column — defaults 25%/75%, the
  // same "smaller side starts smaller" reasoning Base64ImagePanel.tsx's own Upload/Image Data URL
  // split already established (30%/70%), just a bit smaller here since Pattern's own content — a
  // single row of 2 fields plus a caption — needs even less room than a drop-zone does.
  const {
    rowRef: columnRef,
    splitPercent: verticalSplit,
    resizing: verticalResizing,
    minPercent: verticalMin,
    maxPercent: verticalMax,
    handlePointerDown: handleVerticalPointerDown,
    handlePointerMove: handleVerticalPointerMove,
    handlePointerUp: handleVerticalPointerUp,
    handleDoubleClick: handleVerticalDoubleClick,
    handleKeyDown: handleVerticalKeyDown,
  } = useResizableSplit({
    storageKey: 'devUtilsRegexPanelVerticalSplitPercent',
    orientation: 'vertical',
    defaultPercent: 25,
  });

  const updateField = useCallback(
    (field: 'pattern' | 'flags' | 'testText', value: string) => {
      onInputChange(serializeRegexInput({ pattern, flags, testText, [field]: value }));
    },
    [onInputChange, pattern, flags, testText]
  );

  const handlePasteTestText = usePasteText(useCallback(text => updateField('testText', text), [updateField]));

  const handleTest = useCallback(async () => {
    setSaving(true);
    try {
      const result = await onSubmit(input, false);
      onErrorChange(null);
      onOutputChange(result.output);
    } catch (submitError) {
      onOutputChange(null);
      const message = submitError instanceof Error ? submitError.message : 'Could not test this pattern.';
      onErrorChange({ headline: 'Cannot be processed', detail: message });
    } finally {
      setSaving(false);
    }
  }, [input, onSubmit, onOutputChange, onErrorChange]);

  const patternHidden = maximizedPanel === 'testString' || maximizedPanel === 'output';
  const testStringHidden = maximizedPanel === 'pattern' || maximizedPanel === 'output';
  const leftColumnHidden = maximizedPanel === 'output';
  const outputHidden = maximizedPanel === 'pattern' || maximizedPanel === 'testString';

  return (
    // `position: 'relative'` anchors PanelResizeHandle; no `gap` between the two sides — both
    // columns' own flex-basis percentages sum to 100%, mirroring DevUtilToolPanel.tsx's row.
    <Box ref={rowRef} sx={{ position: 'relative', display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start' }}>
      <Box
        ref={columnRef}
        sx={{
          position: 'relative',
          flex: maximizedPanel === 'pattern' || maximizedPanel === 'testString' ? '1 1 100%' : `1 1 ${horizontalSplit}%`,
          minWidth: 320,
          height: availableHeight,
          display: leftColumnHidden ? 'none' : 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        <Paper
          variant="outlined"
          sx={{
            flex: maximizedPanel === 'pattern' ? '1 1 100%' : `1 1 ${verticalSplit}%`,
            minHeight: 0,
            display: patternHidden ? 'none' : 'flex',
            flexDirection: 'column',
            overflow: 'auto',
          }}
        >
          <PanelHeader title="Pattern">
            <MaximizeToggleButton label="Pattern" maximized={maximizedPanel === 'pattern'} onToggle={toggleMaximizePattern} />
          </PanelHeader>
          <Box sx={{ p: 2 }}>
            <Stack direction="row" alignItems="center" spacing={0.5}>
              <Typography sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>/</Typography>
              <TextField
                value={pattern}
                onChange={e => updateField('pattern', e.target.value)}
                placeholder="[\w.+-]+@[\w.-]+\.[a-zA-Z]{2,}"
                fullWidth
                size="small"
                sx={{ '& .MuiInputBase-input': { fontFamily: 'monospace', fontSize: '0.85rem' } }}
              />
              <Typography sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>/</Typography>
              <TextField
                value={flags}
                onChange={e => updateField('flags', e.target.value)}
                placeholder="gi"
                size="small"
                sx={{ width: 72, '& .MuiInputBase-input': { fontFamily: 'monospace', fontSize: '0.85rem' } }}
              />
            </Stack>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
              g global · i case-insensitive · m multiline · s dot matches newline
            </Typography>
          </Box>
        </Paper>

        <PanelResizeHandle
          ariaLabel="Resize Pattern/Test String panels"
          orientation="vertical"
          splitPercent={verticalSplit}
          minPercent={verticalMin}
          maxPercent={verticalMax}
          resizing={verticalResizing}
          hidden={maximizedPanel !== null}
          onPointerDown={handleVerticalPointerDown}
          onPointerMove={handleVerticalPointerMove}
          onPointerUp={handleVerticalPointerUp}
          onDoubleClick={handleVerticalDoubleClick}
          onKeyDown={handleVerticalKeyDown}
        />

        <Paper
          variant="outlined"
          sx={{
            flex: maximizedPanel === 'testString' ? '1 1 100%' : `1 1 ${100 - verticalSplit}%`,
            minHeight: 0,
            display: testStringHidden ? 'none' : 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          <PanelHeader title="Test String">
            <Button size="small" variant="outlined" startIcon={<ContentPasteIcon fontSize="small" />} onClick={handlePasteTestText}>
              Paste
            </Button>
            <SubmitButton
              size="small"
              saving={saving}
              label={actionLabel}
              startIcon={<PlayArrowIcon fontSize="small" />}
              onClick={handleTest}
              disabled={!pattern.trim() || !testText.trim()}
            />
            <MaximizeToggleButton
              label="Test String"
              maximized={maximizedPanel === 'testString'}
              onToggle={toggleMaximizeTestString}
            />
          </PanelHeader>
          {/* `position: 'relative'` + CodeMirror's own `position: 'absolute', inset: 0` (via
              `style`) — the same trick DevUtilToolPanel.tsx's Input editor already relies on to
              fill an ancestor whose own size was arrived at through flex layout rather than a
              plain CSS percentage height, which that component's own history found unreliable in
              practice for this exact "editor fills its flex-sized parent" shape. */}
          <Box sx={{ position: 'relative', flex: 1, minHeight: 0 }}>
            <CodeMirror
              value={testText}
              onChange={value => updateField('testText', value)}
              placeholder={SAMPLE_EMAIL_TEST_TEXT}
              theme="light"
              extensions={[editorChromeTheme]}
              height="100%"
              style={{ position: 'absolute', inset: 0 }}
            />
          </Box>
        </Paper>
      </Box>

      <PanelResizeHandle
        ariaLabel="Resize Pattern/Test String and Output panels"
        splitPercent={horizontalSplit}
        minPercent={horizontalMin}
        maxPercent={horizontalMax}
        resizing={horizontalResizing}
        hidden={maximizedPanel !== null}
        onPointerDown={handleHorizontalPointerDown}
        onPointerMove={handleHorizontalPointerMove}
        onPointerUp={handleHorizontalPointerUp}
        onDoubleClick={handleHorizontalDoubleClick}
        onKeyDown={handleHorizontalKeyDown}
      />

      <Paper
        variant="outlined"
        sx={{
          flex: maximizedPanel === 'output' ? '1 1 100%' : `1 1 ${100 - horizontalSplit}%`,
          minWidth: 320,
          minHeight: availableHeight,
          display: outputHidden ? 'none' : 'flex',
          flexDirection: 'column',
        }}
      >
        <PanelHeader title="Output">
          <Button
            size="small"
            variant="outlined"
            startIcon={<ContentCopyIcon fontSize="small" />}
            onClick={handleCopy}
            disabled={output === null}
          >
            {copiedKey === 'output' ? 'Copied!' : 'Copy'}
          </Button>
          <Tooltip title="Download">
            <span>
              <IconButton size="small" onClick={handleDownload} disabled={output === null}>
                <DownloadIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
          <MaximizeToggleButton label="Output" maximized={maximizedPanel === 'output'} onToggle={toggleMaximizeOutput} />
        </PanelHeader>
        <Box sx={{ p: 2, flex: 1, minHeight: 0, maxHeight: GROWABLE_PANEL_MAX_HEIGHT, overflow: 'auto' }}>
          {error !== null ? (
            <InlineErrorBox error={error} monospaceDetail />
          ) : output !== null ? (
            <Typography
              component="pre"
              sx={{ m: 0, fontFamily: 'monospace', fontSize: '0.85rem', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}
            >
              {output}
            </Typography>
          ) : (
            <Typography variant="body2" color="text.secondary">
              Match results will appear here.
            </Typography>
          )}
        </Box>
      </Paper>
    </Box>
  );
}
