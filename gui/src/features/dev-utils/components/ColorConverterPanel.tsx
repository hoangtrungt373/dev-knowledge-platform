import { useCallback, useEffect, useMemo, useState } from 'react';
import { Box, Button, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import ContentPasteIcon from '@mui/icons-material/ContentPasteOutlined';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import CheckIcon from '@mui/icons-material/CheckOutlined';
import PlayArrowIcon from '@mui/icons-material/PlayArrowOutlined';
import OpenInFullIcon from '@mui/icons-material/OpenInFullOutlined';
import CloseFullscreenIcon from '@mui/icons-material/CloseFullscreenOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import SubmitButton from '@shared/components/SubmitButton';
import { useNotification } from '@shared/contexts/NotificationContext';
import { devUtilsApi } from '../api/devUtilsApi';
import { ColorConversionResponse } from '../types';
import { DevUtilError } from '../utils/errorFormatting';
import { useResizableSplit } from '../hooks/useResizableSplit';
import { usePanelMaximize } from '../hooks/usePanelMaximize';
import PanelHeader from './PanelHeader';
import PanelResizeHandle from './PanelResizeHandle';
import { useCopyFeedback } from '../hooks/useCopyFeedback';

interface ColorConverterPanelProps {
  /** Controlled — same lifted `input` state `DevUtilsPage.tsx` already threads into
   * `DevUtilToolPanel` for every other operation, so this operation's headline row (Sample/Clear)
   * keeps working unchanged even though the rest of this panel's own layout doesn't. Doubles as
   * the hex string both the text field and the native color picker below write to. */
  input: string;
  onInputChange: (value: string) => void;
  inputPlaceholder: string;
  actionLabel: string;
  /** Height (px) computed by `DevUtilsPage.tsx` from the actual viewport — the same value the
   * shared `DevUtilToolPanel`'s Input card and the sidebar both size themselves to. Applied as a
   * `minHeight` floor on both columns (not a fixed `height`) — this operation's own content (a
   * short input row, 6 short result cards) is always small and bounded, the same
   * `HashGeneratorPanel.tsx`-established reasoning: the floor is purely cosmetic, keeping this
   * panel from looking short next to a tall sidebar, not because either side needs to grow. */
  availableHeight: number;
}

// The 3 accepted input shapes — mirror dev-utils-service's own ColorConverter patterns exactly
// (hex, rgb()/rgba(), hsl()/hsla()), so this client-side preview can never silently drift from
// what the backend actually accepts. Only used to compute the picker swatch/border preview below
// — the backend remains the single source of truth for the actual conversion and its own
// (stricter, range-checked) validation.
const HEX_PATTERN = /^#?([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$/;
const RGB_FUNCTION_PATTERN = /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*[\d.]+\s*)?\)$/i;
const HSL_FUNCTION_PATTERN = /^hsla?\(\s*(\d{1,3})\s*,\s*(\d{1,3})%\s*,\s*(\d{1,3})%\s*(?:,\s*[\d.]+\s*)?\)$/i;

function clampByte(n: number): number {
  return Math.min(255, Math.max(0, Math.round(n)));
}

function rgbToHex(r: number, g: number, b: number): string {
  const toHex = (n: number) => clampByte(n).toString(16).padStart(2, '0');
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

// Standard HSL -> RGB conversion (the CSS Color Module's own algorithm — the same one
// dev-utils-service's own ColorConverter#hslToRgb uses server-side), used here only to preview the
// picker swatch/border for an hsl(...) input before the backend ever sees it.
function hueToChannel(p: number, q: number, tIn: number): number {
  let t = tIn;
  if (t < 0) t += 1;
  if (t > 1) t -= 1;
  if (t < 1 / 6) return p + (q - p) * 6 * t;
  if (t < 1 / 2) return q;
  if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
  return p;
}

function hslToHex(h: number, s: number, l: number): string {
  const sFrac = s / 100;
  const lFrac = l / 100;
  if (sFrac === 0) {
    const v = Math.round(lFrac * 255);
    return rgbToHex(v, v, v);
  }
  const q = lFrac < 0.5 ? lFrac * (1 + sFrac) : lFrac + sFrac - lFrac * sFrac;
  const p = 2 * lFrac - q;
  const hFrac = ((h % 360) + 360) % 360 / 360;
  return rgbToHex(
    hueToChannel(p, q, hFrac + 1 / 3) * 255,
    hueToChannel(p, q, hFrac) * 255,
    hueToChannel(p, q, hFrac - 1 / 3) * 255
  );
}

/** Resolves any of the 3 accepted input shapes down to a real `#rrggbb` value for the picker
 * swatch/border preview — `null` for anything that doesn't match any of them (a half-typed value,
 * an out-of-range channel the regex alone can't catch, etc.), letting the caller fall back to a
 * neutral "nothing valid yet" treatment rather than guessing. */
function resolveToHex(rawInput: string): string | null {
  const trimmed = rawInput.trim();
  const rgbMatch = RGB_FUNCTION_PATTERN.exec(trimmed);
  if (rgbMatch) {
    return rgbToHex(Number(rgbMatch[1]), Number(rgbMatch[2]), Number(rgbMatch[3]));
  }
  const hslMatch = HSL_FUNCTION_PATTERN.exec(trimmed);
  if (hslMatch) {
    return hslToHex(Number(hslMatch[1]), Number(hslMatch[2]), Number(hslMatch[3]));
  }
  if (HEX_PATTERN.test(trimmed)) {
    const digits = trimmed.startsWith('#') ? trimmed.slice(1) : trimmed;
    const sixDigit = digits.length === 3 ? digits.replace(/(.)/g, '$1$1') : digits;
    return `#${sixDigit.toLowerCase()}`;
  }
  return null;
}

interface ResultCard {
  key: keyof ColorConversionResponse;
  label: string;
  value: string;
}

function toResultCards(result: ColorConversionResponse): ResultCard[] {
  return [
    { key: 'hex', label: 'HEX', value: result.hex },
    { key: 'rgb', label: 'RGB', value: result.rgb },
    { key: 'hsl', label: 'HSL', value: result.hsl },
    { key: 'cssVariable', label: 'CSS Variable', value: result.cssVariable },
    { key: 'swift', label: 'Swift', value: result.swift },
    { key: 'android', label: 'Android', value: result.android },
  ];
}

/**
 * Color Converter's own bespoke Input/Output layout — a dedicated component, not a mode grafted
 * onto the shared `DevUtilToolPanel.tsx`, per direct request: this operation's Input is a hex/RGB/
 * HSL field plus a native color picker (not a code editor), and its Output is 6 named
 * representations at once (not a syntax-highlighted text block), neither of which fits that
 * component's design — the same "some operations need a genuinely different layout" precedent
 * `HashGeneratorPanel.tsx`/`RegExpTesterPanel.tsx`/etc. already established. `DevUtilsPage.tsx`
 * renders this in place of `DevUtilToolPanel` for exactly this one operation.
 *
 * <p>**Input**: a native `<input type="color">` swatch/picker next to a plain `TextField`, both
 * driving the same lifted `input` string — the same "two input methods, one shared state" pattern
 * `Base64ImagePanel.tsx`'s own Upload/Data-URL pair already established. The text field accepts
 * any of the 3 shapes `dev-utils-service`'s own `ColorConverter` does (a hex color, an
 * `rgb(...)`/`rgba(...)` function, an `hsl(...)`/`hsla(...)` function — per direct follow-up
 * request, originally hex-only since the picker itself only ever produces hex); the picker's own
 * `value` is derived from `input` via `resolveToHex` (a client-side mirror of the backend's own
 * parsing, used only for this live preview — the backend remains the sole source of truth for the
 * actual conversion) — falling back to black for anything not yet valid (e.g. a half-typed value)
 * rather than passed straight through, since the browser's own color input silently ignores a
 * `value` that isn't a real `#rrggbb` string.
 *
 * <p>**Both Input controls get a deliberate, "customized for this operation" border**, per direct
 * follow-up request (the text field's own border was previously hidden, the codebase-wide
 * `HIDDEN_TEXT_FIELD_OUTLINE_SX` convention used to avoid a "box inside a box" look against a
 * bordered `Paper`; that convention was dropped here on purpose): both the picker and the text
 * field share one `inputBorderColor` — the actual resolved color (via `resolveToHex`) while
 * `input` is valid, `error.main` while it's non-blank but doesn't parse, and `divider` while
 * blank — so the border itself doubles as a live "is this valid" indicator, not just decoration.
 *
 * <p>**Output**: 6 named cards (HEX/RGB/HSL/CSS Variable/Swift/Android), each with its own Copy
 * button — the identical shape `HashGeneratorPanel.tsx`'s own per-algorithm cards already
 * establish, plus a large swatch above them showing the actual resolved color for a quick visual
 * check. A failed submit (input that doesn't match any of the 3 accepted shapes) renders inline in
 * the Output panel — the same treatment `RegExpTesterPanel.tsx` already established, picked over a
 * toast since a malformed value is a common, expected outcome while actively typing, not a rare
 * edge case.
 *
 * <p>**Clear also blanks this panel's own local `result`/`error`**, per direct bug report — this
 * panel's richer `ColorConversionResponse`/`DevUtilError` state can't be lifted into
 * `DevUtilsPage.tsx`'s own plain `output: string | null` (see that page's own doc comment), so
 * `DevUtilsPage.tsx`'s Clear button only ever reset the lifted `input` back to `''`, leaving this
 * panel's own local result/error stuck on screen. Fixed the same way `TextDiffPanel.tsx` already
 * did for the identical underlying gap: a `useEffect` watching `input` clears both local pieces of
 * state the instant it goes blank.
 *
 * <p>Same resizable-split-plus-maximize mechanism every custom panel in this feature now shares —
 * a single horizontal split (`hooks/useResizableSplit.ts`'s default orientation — this operation
 * only ever has 2 cards, not 3, so there's no second stacked card to divide) plus a 2-way
 * `usePanelMaximize<'input' | 'output'>()` toggle, the same 2-panel shape `DevUtilToolPanel.tsx`/
 * `HashGeneratorPanel.tsx` themselves use.
 */
export default function ColorConverterPanel({
  input,
  onInputChange,
  inputPlaceholder,
  actionLabel,
  availableHeight,
}: ColorConverterPanelProps): JSX.Element {
  const { showError } = useNotification();
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<ColorConversionResponse | null>(null);
  const [error, setError] = useState<DevUtilError | null>(null);
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
  } = useResizableSplit({ storageKey: 'devUtilsColorConverterPanelSplitPercent' });

  // resolveToHex over the placeholder (when input is empty) so the picker/border preview a
  // sensible color before the user has typed anything, the same "preview the placeholder" idea
  // Sample already relies on elsewhere in this feature — never sent anywhere, purely visual.
  const resolvedPreviewHex = useMemo(() => resolveToHex(input || inputPlaceholder), [input, inputPlaceholder]);
  const pickerValue = resolvedPreviewHex ?? '#000000';
  // Drives both Input controls' own border — the actual resolved color while valid, an error tint
  // while non-blank but unparseable, or the plain divider while blank. See this component's own
  // doc comment for the full reasoning.
  const inputBorderColor = !input.trim() ? 'divider' : resolvedPreviewHex ?? 'error.main';

  // Mirrors DevUtilsPage.tsx's own Clear behavior for every other panel (blanking `input` also
  // blanks its own local output) — this panel's "result"/"error" can't be lifted into that page's
  // plain `string | null` state (see this component's own doc comment for why), so it clears its
  // own local copies whenever Clear resets the lifted `input` back to `''`. Real bug fix, reported
  // directly ("the Output content does not clear when I click Clear") — mirrors the identical fix
  // `TextDiffPanel.tsx` already established for the same underlying gap.
  useEffect(() => {
    if (input === '') {
      setResult(null);
      setError(null);
    }
  }, [input]);

  const handleConvert = useCallback(async () => {
    setSaving(true);
    try {
      const converted = await devUtilsApi.convertColor(input);
      setError(null);
      setResult(converted);
    } catch (submitError) {
      setResult(null);
      const message = submitError instanceof Error ? submitError.message : 'Could not convert this color.';
      setError({ headline: 'Cannot be processed', detail: message });
    } finally {
      setSaving(false);
    }
  }, [input]);

  const handlePaste = useCallback(async () => {
    try {
      const text = await navigator.clipboard.readText();
      onInputChange(text);
    } catch {
      showError('Could not read from the clipboard — check your browser permissions.');
    }
  }, [onInputChange, showError]);

  const handleCopyValue = useCallback((key: string, value: string) => copy(value, key), [copy]);

  const cards = result !== null ? toResultCards(result) : [];
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
          minHeight: availableHeight,
          display: inputHidden ? 'none' : 'block',
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
            onClick={handleConvert}
            disabled={!input.trim()}
          />
          <Tooltip title={maximizedPanel === 'input' ? 'Restore split view' : 'Maximize Input'}>
            <IconButton size="small" onClick={toggleMaximizeInput}>
              {maximizedPanel === 'input' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </PanelHeader>
        <Box sx={{ p: 2 }}>
          <Stack direction="row" spacing={2} alignItems="center">
            {/* A native color picker — its own `value` must always be a real #rrggbb string, so it
                reads from the derived `pickerValue`, never `input` directly. Picking a color
                writes the picker's own (always lowercase, always 6-digit) value straight back to
                `input`. Border color is `inputBorderColor` — the resolved color itself while
                valid, so this frame doubles as a second, more prominent swatch alongside the fill
                underneath it. */}
            <Box
              component="input"
              type="color"
              value={pickerValue}
              onChange={e => onInputChange(e.target.value)}
              aria-label="Pick a color"
              sx={{
                width: 40,
                height: 40,
                p: 0,
                border: '2px solid',
                borderColor: inputBorderColor,
                borderRadius: 1,
                cursor: 'pointer',
                flexShrink: 0,
                transition: 'border-color 0.15s ease',
                // Strip the browser's own default swatch padding/border so the color fills this
                // box edge-to-edge, matching the rounded-corner frame drawn around it above.
                '&::-webkit-color-swatch-wrapper': { p: 0 },
                '&::-webkit-color-swatch': { border: 'none', borderRadius: 1 },
              }}
            />
            {/* The text field's own border was previously hidden (this feature's usual
                HIDDEN_TEXT_FIELD_OUTLINE_SX convention, used to avoid a "box inside a box" look
                against this card's own Paper border) — deliberately reinstated and customized here
                per direct request: a visible, color-reactive 2px border, matching the picker's own
                frame, across all 3 states (default/hover/focus — MUI's own higher-specificity
                hover/focus rules need their own explicit override, the same specificity gotcha
                already documented elsewhere in this feature, e.g. DevUtilSidebarItem.tsx's own
                selected-row background). */}
            <TextField
              value={input}
              onChange={e => onInputChange(e.target.value)}
              placeholder={inputPlaceholder}
              fullWidth
              size="small"
              sx={{
                '& .MuiInputBase-input': { fontFamily: 'monospace', fontSize: '0.9rem' },
                '& .MuiOutlinedInput-notchedOutline': {
                  borderWidth: 2,
                  borderColor: inputBorderColor,
                  transition: 'border-color 0.15s ease',
                },
                '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: inputBorderColor },
                '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: inputBorderColor, borderWidth: 2 },
              }}
            />
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            A hex color (#RRGGBB), rgb(R, G, B), or hsl(H, S%, L%)
          </Typography>
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
          minHeight: availableHeight,
          display: outputHidden ? 'none' : 'flex',
          flexDirection: 'column',
        }}
      >
        <PanelHeader title="Output">
          <Tooltip title={maximizedPanel === 'output' ? 'Restore split view' : 'Maximize Output'}>
            <IconButton size="small" onClick={toggleMaximizeOutput}>
              {maximizedPanel === 'output' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </PanelHeader>
        <Box sx={{ p: 2, flex: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
          {error !== null ? (
            <Stack
              direction="row"
              spacing={1.5}
              sx={{
                p: 2,
                borderRadius: 1,
                border: '1px solid',
                borderColor: 'error.main',
                bgcolor: theme => alpha(theme.palette.error.main, 0.08),
              }}
            >
              <ErrorOutlineIcon fontSize="small" color="error" sx={{ mt: '2px' }} />
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="subtitle2" fontWeight={700} color="error.main">
                  {error.headline}
                </Typography>
                <Typography variant="body2" color="text.primary" sx={{ wordBreak: 'break-word' }}>
                  {error.detail}
                </Typography>
              </Box>
            </Stack>
          ) : cards.length === 0 ? (
            <Paper
              variant="outlined"
              sx={{ p: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'background.paper' }}
            >
              <Typography variant="body2" color="text.secondary">
                Converted color will appear here.
              </Typography>
            </Paper>
          ) : (
            <>
              <Box
                sx={{
                  height: 48,
                  borderRadius: 1,
                  border: '1px solid',
                  borderColor: 'divider',
                  bgcolor: result?.hex,
                }}
              />
              {cards.map(({ key, label, value }) => (
                <Paper key={key} variant="outlined" sx={{ p: 2, bgcolor: '#ffffff' }}>
                  <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
                    <Typography variant="subtitle2" fontWeight={700} sx={{ color: 'grey.900' }}>
                      {label}
                    </Typography>
                    <Tooltip title={copiedKey === key ? 'Copied!' : 'Copy'}>
                      <IconButton size="small" onClick={() => handleCopyValue(key, value)}>
                        {copiedKey === key ? (
                          <CheckIcon fontSize="small" color="success" />
                        ) : (
                          <ContentCopyIcon fontSize="small" />
                        )}
                      </IconButton>
                    </Tooltip>
                  </Stack>
                  <Typography
                    component="pre"
                    sx={{ m: 0, color: 'grey.800', fontFamily: 'monospace', fontSize: '0.85rem', whiteSpace: 'pre-wrap' }}
                  >
                    {value}
                  </Typography>
                </Paper>
              ))}
            </>
          )}
        </Box>
      </Paper>
    </Box>
  );
}
