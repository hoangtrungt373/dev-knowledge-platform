import { useCallback, useState } from 'react';
import { Box, Button, IconButton, Paper, Slider, Stack, Tooltip, Typography } from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import DownloadIcon from '@mui/icons-material/DownloadOutlined';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesomeOutlined';
import OpenInFullIcon from '@mui/icons-material/OpenInFullOutlined';
import CloseFullscreenIcon from '@mui/icons-material/CloseFullscreenOutlined';
import SubmitButton from '@shared/components/SubmitButton';
import { useNotification } from '@shared/contexts/NotificationContext';
import { devUtilsApi } from '../api/devUtilsApi';
import { downloadTextFile } from '../utils/downloadTextFile';
import { GROWABLE_PANEL_MAX_HEIGHT } from '../config/panelSizing';
import { useResizableSplit } from '../hooks/useResizableSplit';
import { usePanelMaximize } from '../hooks/usePanelMaximize';
import PanelHeader from './PanelHeader';
import PanelResizeHandle from './PanelResizeHandle';
import { useCopyFeedback } from '../hooks/useCopyFeedback';

interface LoremIpsumPanelProps {
  /** Controlled — same lifted `input`/`output` state `DevUtilsPage.tsx` already threads into
   * `DevUtilToolPanel` for every other operation, so this operation's headline row (Sample/Clear)
   * and Clear button keep working unchanged even though the rest of this panel's own layout
   * doesn't. `input` doubles as the serialized paragraph count — a plain numeric string (e.g.
   * `"5"`), not a richer serialized shape the way `RegExpTesterPanel.tsx`'s
   * `utils/regexInputFormat.ts` needs, since there's only ever this one field to carry. */
  input: string;
  onInputChange: (value: string) => void;
  output: string | null;
  onOutputChange: (value: string | null) => void;
  actionLabel: string;
  downloadFileName: string;
  /** Height (px) computed by `DevUtilsPage.tsx` from the actual viewport — see
   * `HashGeneratorPanel.tsx`'s own doc comment for the full reasoning behind applying this as a
   * `minHeight` floor (not a fixed `height`) on both sides here. */
  availableHeight: number;
}

/** Default paragraph count when `input` is blank (nothing picked yet, or just Cleared) —
 * generous enough to demonstrate the operation without immediately maxing out the slider. */
const DEFAULT_PARAGRAPHS = 3;
const MIN_PARAGRAPHS = 1;
const MAX_PARAGRAPHS = 20;
const PARAGRAPH_MARKS = [1, 5, 10, 15, 20].map(value => ({ value, label: String(value) }));

function clampParagraphs(value: number): number {
  return Math.min(MAX_PARAGRAPHS, Math.max(MIN_PARAGRAPHS, Math.round(value)));
}

function parseParagraphs(input: string): number {
  if (input.trim() === '') {
    return DEFAULT_PARAGRAPHS;
  }
  const parsed = Number(input);
  return Number.isFinite(parsed) ? clampParagraphs(parsed) : DEFAULT_PARAGRAPHS;
}

/**
 * Lorem Ipsum Generator's own bespoke Input/Output layout — a dedicated component, not a mode
 * grafted onto the shared `DevUtilToolPanel.tsx`, per direct request ("Allow user to input amount
 * of paragraph (1, 3, 5, ... 20)"): this operation's own Input isn't free text at all, it's a
 * single bounded count — a `TextField`-backed code editor would be the wrong control for picking
 * a number 1-20, the same "some operations need a genuinely different layout" precedent
 * `HashGeneratorPanel.tsx`/`Base64ImagePanel.tsx`/etc. already established.
 *
 * <p>**Entirely client-driven on the Generate side** — a discrete MUI `Slider` (1-20, marked at
 * 1/5/10/15/20) is the one control, since a slider is the natural UI for picking a bounded integer
 * count, the same reason a quantity stepper (not a free-text box) is used for a shopping-cart
 * quantity elsewhere in this app. No Paste button here (unlike every other custom panel's own
 * "Generate"/"Read"-style card) — there's no text to paste into a count picker.
 *
 * <p>**Deliberately much simpler than `RegExpTesterPanel.tsx`/`TextDiffPanel.tsx`** — only 2 cards
 * (Generate, Output), not 3, so there's a single horizontal split and a 2-way
 * `usePanelMaximize<'generate' | 'output'>()` toggle, the same 2-panel shape
 * `HashGeneratorPanel.tsx`/`DevUtilToolPanel.tsx` themselves use. Both `output`/`onOutputChange`
 * are lifted into `DevUtilsPage.tsx` (not local component state) from the start — avoiding the
 * "Clear doesn't blank the Output panel" bug `ColorConverterPanel.tsx` originally had to be fixed
 * for after the fact (see that component's own history), rather than repeating it here.
 *
 * <p>This operation never fails once the count itself is valid — there's no notion of "invalid"
 * placeholder text — so a submit failure (a genuine network/technical error) surfaces via a plain
 * `showError` toast, the same treatment `HashGeneratorPanel.tsx` already uses for the identical
 * reason, rather than `RegExpTesterPanel.tsx`'s own inline error box (which exists specifically
 * for an *expected*, common failure mode this operation doesn't have).
 */
export default function LoremIpsumPanel({
  input,
  onInputChange,
  output,
  onOutputChange,
  actionLabel,
  downloadFileName,
  availableHeight,
}: LoremIpsumPanelProps): JSX.Element {
  const { showError } = useNotification();
  const [generating, setGenerating] = useState(false);
  const { copiedKey, copy } = useCopyFeedback();

  const { maximizedPanel, toggle: toggleMaximize } = usePanelMaximize<'generate' | 'output'>();
  const toggleMaximizeGenerate = useCallback(() => toggleMaximize('generate'), [toggleMaximize]);
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
  } = useResizableSplit({ storageKey: 'devUtilsLoremIpsumPanelSplitPercent' });

  const paragraphs = parseParagraphs(input);

  const handleSliderChange = useCallback(
    (_event: Event, value: number | number[]) => {
      onInputChange(String(Array.isArray(value) ? value[0] : value));
    },
    [onInputChange]
  );

  const handleGenerate = useCallback(async () => {
    setGenerating(true);
    try {
      const result = await devUtilsApi.generateLoremIpsum(paragraphs);
      onOutputChange(result.output);
    } catch (submitError) {
      onOutputChange(null);
      const message = submitError instanceof Error ? submitError.message : 'Could not generate Lorem Ipsum text.';
      showError(message);
    } finally {
      setGenerating(false);
    }
  }, [paragraphs, onOutputChange, showError]);

  const handleCopy = useCallback(() => {
    if (output === null) return;
    copy(output, 'output');
  }, [output, copy]);

  const handleDownload = useCallback(() => {
    if (output === null) return;
    downloadTextFile(downloadFileName, output);
  }, [output, downloadFileName]);

  const generateHidden = maximizedPanel === 'output';
  const outputHidden = maximizedPanel === 'generate';

  return (
    // `position: 'relative'` anchors PanelResizeHandle; no `gap` — both sides' own flex-basis
    // percentages sum to 100%, mirroring every other resizable split in this feature.
    <Box ref={rowRef} sx={{ position: 'relative', display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start' }}>
      <Paper
        variant="outlined"
        sx={{
          flex: maximizedPanel === 'generate' ? '1 1 100%' : `1 1 ${splitPercent}%`,
          minWidth: 320,
          minHeight: availableHeight,
          display: generateHidden ? 'none' : 'block',
        }}
      >
        <PanelHeader title="Generate">
          <SubmitButton
            size="small"
            saving={generating}
            label={actionLabel}
            startIcon={<AutoAwesomeIcon fontSize="small" />}
            onClick={handleGenerate}
          />
          <Tooltip title={maximizedPanel === 'generate' ? 'Restore split view' : 'Maximize Generate'}>
            <IconButton size="small" onClick={toggleMaximizeGenerate}>
              {maximizedPanel === 'generate' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </PanelHeader>
        <Box sx={{ p: 3, maxWidth: 480 }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            Paragraphs
          </Typography>
          <Stack direction="row" alignItems="center" spacing={2}>
            <Slider
              value={paragraphs}
              onChange={handleSliderChange}
              min={MIN_PARAGRAPHS}
              max={MAX_PARAGRAPHS}
              step={1}
              marks={PARAGRAPH_MARKS}
              valueLabelDisplay="auto"
              sx={{ flex: 1 }}
            />
            <Typography variant="h6" fontWeight={700} sx={{ minWidth: 32, textAlign: 'center' }}>
              {paragraphs}
            </Typography>
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            Classic Lorem Ipsum placeholder text — the first paragraph always opens with the
            traditional "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
          </Typography>
        </Box>
      </Paper>

      <PanelResizeHandle
        ariaLabel="Resize Generate/Output panels"
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
          <Tooltip title={maximizedPanel === 'output' ? 'Restore split view' : 'Maximize Output'}>
            <IconButton size="small" onClick={toggleMaximizeOutput}>
              {maximizedPanel === 'output' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </PanelHeader>
        <Box sx={{ p: 2, flex: 1, minHeight: 0, maxHeight: GROWABLE_PANEL_MAX_HEIGHT, overflow: 'auto' }}>
          {output !== null ? (
            <Typography
              component="pre"
              sx={{ m: 0, fontFamily: 'monospace', fontSize: '0.85rem', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}
            >
              {output}
            </Typography>
          ) : (
            <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Typography variant="body2" color="text.secondary">
                Generated text will appear here.
              </Typography>
            </Box>
          )}
        </Box>
      </Paper>
    </Box>
  );
}
