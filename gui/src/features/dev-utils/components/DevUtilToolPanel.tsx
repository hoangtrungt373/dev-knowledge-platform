import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Box, Button, IconButton, Paper, Stack, Tooltip, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import ContentPasteIcon from '@mui/icons-material/ContentPasteOutlined';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import PlayArrowIcon from '@mui/icons-material/PlayArrowOutlined';
import DownloadIcon from '@mui/icons-material/DownloadOutlined';
import UnfoldLessIcon from '@mui/icons-material/UnfoldLessOutlined';
import OpenInFullIcon from '@mui/icons-material/OpenInFullOutlined';
import CloseFullscreenIcon from '@mui/icons-material/CloseFullscreenOutlined';
// Already an outline-style glyph under its own distinct name (not the "Outlined" suffix
// convention every other icon above uses) — MUI ships "Error" (filled) and "ErrorOutline" as two
// separately named icons, not a base/Outlined pair, so there's no further outlined variant to
// switch to here.
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import CodeMirror from '@uiw/react-codemirror';
import { vscodeDark } from '@uiw/codemirror-theme-vscode';
import SubmitButton from '@shared/components/SubmitButton';
import { useNotification } from '@shared/contexts/NotificationContext';
import { DevUtilsResponse } from '../types';
import { buildDevUtilError, DevUtilError } from '../utils/errorFormatting';
import { OUTPUT_LANGUAGE_INFO, OutputLanguage } from '../config/outputLanguages';
import { editorChromeTheme, getCodeMirrorExtensions } from '../config/codeMirrorConfig';
import { GROWABLE_PANEL_MAX_HEIGHT } from '../config/panelSizing';
import { useResizableSplit } from '../hooks/useResizableSplit';
import { usePanelMaximize } from '../hooks/usePanelMaximize';
import PanelHeader from './PanelHeader';
import PanelResizeHandle from './PanelResizeHandle';
import { useCopyFeedback } from '../hooks/useCopyFeedback';
import { downloadTextFile } from '../utils/downloadTextFile';

interface DevUtilToolPanelProps {
  /** Controlled — lifted up to `DevUtilsPage.tsx` so its own headline row's Sample/Clear buttons
   * can set/reset it directly, alongside this panel's own Paste button and typing. */
  input: string;
  onInputChange: (value: string) => void;
  /** Controlled too, for the same reason as `input` — the headline row's Clear button needs to
   * blank the Output panel alongside the Input one. */
  output: string | null;
  onOutputChange: (value: string | null) => void;
  /** Controlled too — a failed submit renders inline in the Output panel instead of a header
   * notification, so the headline row's Clear button needs to be able to dismiss it as well.
   * Mutually exclusive with `output` — `handleSubmit` always clears one before setting the other. */
  error: DevUtilError | null;
  onErrorChange: (error: DevUtilError | null) => void;
  actionLabel: string;
  inputPlaceholder: string;
  /** What format the *input* box holds. Branched on for the literal `'json'` (picks
   * `buildDevUtilError`'s client-side `JSON.parse` fast path) and to pick the Input editor's own
   * CodeMirror language extension (`config/codeMirrorConfig.ts#getCodeMirrorExtensions`); every
   * other value otherwise just takes `buildDevUtilError`'s own doc comment for exactly which
   * operations' backend can genuinely reject their input (and therefore ever actually populate its
   * fallback path). */
  inputFormat: 'json' | 'yaml' | 'html' | 'css' | 'less' | 'scss' | 'js' | 'erb' | 'xml' | 'csv' | 'sql' | 'php' | 'text';
  /** Language id for the Output editor's own syntax highlighting — also the key into
   * `config/outputLanguages.ts#OUTPUT_LANGUAGE_INFO` for this panel's own info-row badge, and into
   * `config/codeMirrorConfig.ts#getCodeMirrorExtensions` for its CodeMirror language extension. */
  outputLanguage: OutputLanguage;
  /** Whether this tool exposes a minify checkbox at all — false only for JSON→YAML, which has no
   * minify concept (see devUtilsApi.jsonToYaml's own comment). */
  supportsMinify: boolean;
  /** Filename offered by the Output panel's Download button, e.g. "formatted.json". */
  downloadFileName: string;
  onSubmit: (input: string, minify: boolean) => Promise<DevUtilsResponse>;
  /** A second, independent action button next to the primary one — e.g. Base64's own "Encode"/
   * "Decode" pair, run against the same `input`. Omitted entirely (not just disabled) for every
   * operation that only ever has one action, the same "absent, not a disabled/hidden state" rule
   * `supportsMinify: false` already follows for the Minify toggle. Shares the primary action's own
   * `(input, minify)` signature purely for consistency, even though an operation that needs a
   * second action in the first place is unlikely to also support `minify` (Base64 doesn't) — see
   * this component's own doc comment for the full reasoning and why this isn't instead generalized
   * into an arbitrary-length actions array. */
  secondaryAction?: {
    label: string;
    onSubmit: (input: string, minify: boolean) => Promise<DevUtilsResponse>;
  };
  /** Height (px) computed by `DevUtilsPage.tsx` from the actual viewport — see that component's
   * own doc comment for how. The Input card renders at exactly this height (`height:
   * availableHeight`); the Output card instead uses it only as a **floor** (`minHeight:
   * availableHeight`) — see this component's own doc comment for why the two differ. */
  availableHeight: number;
}

// The Output panel's background is state-driven, per request — white by default (no result yet,
// or a failed submit), switching to the Output editor's own dark theme
// (`@uiw/codemirror-theme-vscode`'s `vscodeDark` — a real VS Code Dark+ port, not a hand-tuned
// approximation) only once a real result is showing. `OUTPUT_BG_LIGHT` is a fixed literal, not a
// theme token (`background.paper` etc.) — this box's own color scheme is deliberately independent
// of the app's light/dark mode toggle, the same way a code editor's own theme doesn't follow the
// surrounding app's chrome; `vscodeDark` carries the matching dark background internally, so
// there's no equivalent `OUTPUT_BG_DARK` constant needed on this side anymore.
const OUTPUT_BG_LIGHT = '#ffffff';
// The light theme's own error red (`shared/constants/colors.ts`'s BRAND_COLORS.light.error) — used
// literally rather than the theme's own `error.main` token, since that token swaps to a brighter
// red tuned for a dark surface once the app is in dark mode, which would look wrong against this
// panel's always-white error background.
const OUTPUT_ERROR_COLOR = '#cf222e';
// A shade lighter than vscodeDark's own background, per request ("use bgColor black also, less
// black than the content") — VS Code Dark+'s own toolbar/sidebar tone, distinguishing the info row
// from the code content below it without breaking from the dark, theme-independent look this panel
// already has.
const OUTPUT_INFO_BG = '#252526';
// A muted, de-emphasized grey for the file-name value, per request — it isn't the focused content
// (the response itself is), so it stays visually secondary rather than reading as bright/prominent
// text. Still light enough to stay legible against the dark backgrounds, just clearly dimmer than
// the response text itself or the file-type badge colors.
const OUTPUT_FILENAME_COLOR = '#6e7681';
// A fixed mid-dark grey (VS Code's own default border/separator tone) used for the info row's own
// border-bottom — deliberately not the theme's `divider` token, which is a translucent black/white
// that barely shows up against a hardcoded dark background (or, in one case, disappears into it
// depending on the app's own light/dark mode), the same "fixed literal, not a theme token"
// reasoning as this panel's other colors. Reads as a normal dark rule against the white content
// states too.
const OUTPUT_LINE_COLOR = '#3c3c3c';

/** The Input/Output split panel every /dev-utils tool renders — each side is its own bordered
 * card, per request, with its action buttons on the same line as its own title ("Input   [Paste]
 * [<actionLabel>] [Minify]" / "Output   [Copy] [Download]") rather than a separate toolbar row.
 * Minify is its own toggle button (`variant` swaps outlined/contained to show pressed state),
 * not a `Checkbox`, per a follow-up request — positioned after the action button, and omitted
 * entirely (not just disabled) when the operation doesn't support it. Each tab configures this
 * for its own operation rather than this component knowing about any specific one.
 *
 * <p>**Both Input and Output are real CodeMirror 6 editors** (`@uiw/react-codemirror`), not a plain
 * `TextField`/read-only `react-syntax-highlighter` block — a follow-up request to replace the
 * original plain pairing for a large payload, after discussing CodeMirror vs. Monaco (CodeMirror
 * chosen: far lighter — this app's bundle is already flagged for size — with no web worker/CDN
 * story to manage, at the cost of Monaco's more IDE-like feel) and "both panels" vs. "Output only"
 * (both chosen, so Input gets real syntax highlighting for whatever it's typing/pasting too,
 * matching its own `inputFormat`). `react-syntax-highlighter` itself is **not** removed as a
 * dependency — `@chat/components/MarkdownRenderer.tsx` and `@content/components/MarkdownField.tsx`
 * both still use it; only this file stopped. `config/codeMirrorConfig.ts#getCodeMirrorExtensions`
 * maps every `inputFormat`/`outputLanguage` id this feature's operations pass to the matching
 * CodeMirror language extension (`erb`/`csv`/`text` fall back to plain, unhighlighted text — see
 * that file's own comment for why). `config/codeMirrorConfig.ts#editorChromeTheme` is a small
 * shared `EditorView.theme()` extension (font size, content padding) applied to both editors, so
 * the code area keeps the same `16px` inset every other surface in this panel already uses instead
 * of CodeMirror's own, noticeably tighter default.
 *
 * <p>`input`, `output`, and `error` are all controlled props, not local state — lifted up to
 * `DevUtilsPage.tsx` once that page's own headline row needed Sample/Clear buttons able to
 * set/reset them directly (this panel's own Paste button and the editor's own typing both just
 * call `onInputChange` now, the same as that page's own callers; a submit calls `onOutputChange`
 * on success or `onErrorChange` on failure instead of local setters). Every other piece of state
 * here (`minify`/`saving`/`copied`) stays local — `DevUtilsPage.tsx` still remounts this component
 * on tool switch (`key={...}`) to reset those, independently of the parent's own `input`/`output`/
 * `error` reset. `splitPercent` (from `useResizableSplit`, the resizable divider's own share of
 * the row, below) is deliberately **not** reset on tool switch — a standing per-viewer layout
 * preference (persisted to `localStorage` under that hook's own `storageKey`), not something
 * specific to whichever tool happens to be selected right now.
 *
 * <p>A submit failure is **not** surfaced via the header notification (`showNotification`/
 * `showError`) at all anymore, per a direct request — `onSubmit`'s own `devUtilsApi.*` calls are
 * wired up with no `showError` argument (see `DevUtilsPage.tsx`'s own `onSubmit` closures), so
 * `httpClient` never fires that toast for these four operations; the thrown `Error`'s `.message`
 * is instead fed through `errorFormatting.ts#buildDevUtilError` and rendered inline in the Output
 * panel below (see that function's own doc comment for exactly what it does with the raw backend
 * message, and why the two JSON-input operations bypass it entirely in favor of the browser's own
 * `JSON.parse`).
 *
 * <p>The Output panel's own background is state-driven, per request — white (`OUTPUT_BG_LIGHT`)
 * for both the empty placeholder and an `error`, switching to `vscodeDark`'s own dark background
 * only once `output` actually holds a real result. This deliberately reintroduces the white →
 * black transition an earlier fix had removed (see git history/`docs/CHANGELOG.md` around that fix
 * if picking through this box's own color history) — that transition is the explicit ask here, not
 * an oversight.
 *
 * <p>Between the header row and the content area, a small info row shows a single
 * "{@code <TYPE> | <filename>}" line — no "File type:"/"File name:" labels — but **only once
 * `output` actually holds a real result**, per a follow-up request; it renders `null` for the
 * empty-placeholder and `error` states, unlike an earlier version of this row that showed it
 * unconditionally across all three (don't reintroduce that without confirming it's wanted again).
 * Both values are already known statically per operation (`outputLanguage`/`downloadFileName`), so
 * nothing here is actually derived from the response itself — only the decision of *whether* to
 * show them is now response-gated. `config/outputLanguages.ts#OUTPUT_LANGUAGE_INFO` maps the
 * Prism-derived language id (`outputLanguage`) each operation already passes to a human label
 * (`JSON`/`YAML`/`HTML`/etc.) and a per-language badge color, one shared map so the two can never
 * drift out of sync with each other (see that file's own doc comment — it used to be two
 * independently-maintained `Record<string, string>`s here). Its background is a fixed
 * `OUTPUT_INFO_BG` (a shade lighter than `vscodeDark`'s own background — always dark, unlike the
 * content area below it, which still switches white/black by state) rather than a theme token,
 * same "independent of the app's light/dark toggle" reasoning as this panel's other colors. The
 * `<TYPE>` segment is colored per language (common language-badge convention) and bold; the `|`
 * separator and the filename both use the muted `OUTPUT_FILENAME_COLOR` grey, since neither is the
 * focused content — the response itself is. All three segments are plain `<Box component="span">`s
 * inside one `Typography`, not separate flex-positioned elements — a simpler one-line rendering
 * superseded an earlier attempt at horizontally aligning the type/filename with the line-number/
 * response columns beneath them (that column-alignment scheme, plus a full-height vertical
 * gutter-divider line and a baseline-mismatch fix it needed, were all tried in earlier passes and
 * then explicitly simplified away per a direct request for this plainer template — don't
 * reintroduce that alignment complexity without confirming it's wanted again). The Output editor's
 * own line-number gutter (from CodeMirror's default `basicSetup`, styled by `vscodeDark`) replaces
 * the old hand-tuned `!important` line-number color override react-syntax-highlighter needed — a
 * real VS Code theme port already gets this right natively.
 *
 * <p>The info row's own border-bottom uses a fixed `OUTPUT_LINE_COLOR` rather than the theme's
 * `divider` token — `divider` is a translucent black/white overlay tuned for the app's own
 * background, which barely shows (or vanishes, depending on light/dark app mode) against this
 * panel's hardcoded dark background.
 *
 * <p>**Either panel can be "maximized"** (a header `IconButton`, `OpenInFullIcon`/
 * `CloseFullscreenIcon`), per request — for reading/scrolling a large result without the other
 * side sharing the row's width. See `usePanelMaximize`'s own doc comment for why it's plain
 * component state (resets on tool switch, not a standing `localStorage` preference like
 * `useResizableSplit`'s own ratio) and why the un-maximized side is hidden via `display: 'none'`
 * rather than unmounted. Width-only, deliberately — Output's own height already grows
 * independently of Input (floor at `availableHeight`, capped at `GROWABLE_PANEL_MAX_HEIGHT`, both
 * already documented above), so maximizing doesn't need its own separate height story on top of
 * that; it only ever changes which Paper gets the row's full width via `flex-basis`. The resize
 * handle hides too while either panel is maximized — nothing to drag when one side isn't
 * rendered.
 *
 * <p>**A second, independent action button** (`secondaryAction`) is how an operation with two
 * genuinely different actions over the same input — Base64's own "Encode"/"Decode" pair is the
 * first one — renders both, rather than picking one as primary and stretching the other into a
 * `Minify`-style toggle (the two aren't a base/compact-form pair the way `minify` is elsewhere in
 * this panel; each produces a fully independent result). `savingAction` (`'primary' | 'secondary'
 * | null`), not two separate booleans, tracks which one is in flight — submitting either disables
 * *both* buttons until it resolves, preventing an overlapping double-submit against the same
 * `input`/`onOutputChange`/`onErrorChange` state this component only has one copy of. Deliberately
 * not generalized into an arbitrary-length actions array — every operation today needs either one
 * action or exactly two, and YAGNI applies the same way it does elsewhere in this codebase: build
 * for two concrete buttons, not a hypothetical N. */
export default function DevUtilToolPanel({
  input,
  onInputChange,
  output,
  onOutputChange,
  error,
  onErrorChange,
  actionLabel,
  inputPlaceholder,
  inputFormat,
  outputLanguage,
  supportsMinify,
  downloadFileName,
  onSubmit,
  secondaryAction,
  availableHeight,
}: DevUtilToolPanelProps): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [minify, setMinify] = useState(false);
  // Tracks *which* action is in flight, not just whether one is — with two independent action
  // buttons (the primary one plus an optional `secondaryAction`), a single boolean `saving` would
  // either spin both buttons at once for a single click or need a second boolean anyway. `null`
  // means neither; each button's own `saving`/`disabled` props below derive from comparing against
  // this rather than a per-button state pair, so the two can never independently claim "saving" at
  // the same time (submitting one disables the other, preventing an overlapping double-submit).
  const [savingAction, setSavingAction] = useState<'primary' | 'secondary' | null>(null);
  const { copiedKey, copy } = useCopyFeedback();

  // Memoized so a re-render (e.g. every keystroke while typing in Input) doesn't hand CodeMirror a
  // brand-new extensions array reference each time — `inputFormat`/`outputLanguage` are constant
  // for this component's whole mounted lifetime anyway (a tool switch remounts it via `key={...}`
  // in DevUtilsPage.tsx), so this only ever actually recomputes once per mount regardless.
  const inputExtensions = useMemo(() => getCodeMirrorExtensions(inputFormat), [inputFormat]);
  const outputExtensions = useMemo(() => getCodeMirrorExtensions(outputLanguage), [outputLanguage]);

  // "Maximize this panel" and the resizable Input/Output split are both extracted into shared
  // hooks now (`usePanelMaximize`/`useResizableSplit`) — this component was their original
  // implementation, moved out once `RegExpTesterPanel.tsx`/`TextDiffPanel.tsx` needed the
  // identical mechanisms for their own Input/Output-shaped pair. See each hook's own doc comment
  // for the full reasoning (why maximize is plain component state but the split ratio is
  // persisted, why the split is hand-rolled with Pointer Events rather than
  // `react-resizable-panels`, etc.) — unchanged here, just no longer inlined.
  const { maximizedPanel, toggle: toggleMaximize } = usePanelMaximize<'input' | 'output'>();
  const toggleMaximizeInput = useCallback(() => toggleMaximize('input'), [toggleMaximize]);
  const toggleMaximizeOutput = useCallback(() => toggleMaximize('output'), [toggleMaximize]);

  const {
    rowRef,
    splitPercent,
    resizing,
    minPercent: minSplitPercent,
    maxPercent: maxSplitPercent,
    handlePointerDown: handleResizePointerDown,
    handlePointerMove: handleResizePointerMove,
    handlePointerUp: handleResizePointerUp,
    handleDoubleClick: handleResizeDoubleClick,
    handleKeyDown: handleResizeKeyDown,
  } = useResizableSplit({ storageKey: 'devUtilsPanelSplitPercent' });

  // Output's own header row + (conditionally) the info row sit above the code area inside the
  // same Paper — measured together here as one real pixel value, rather than assumed, so the
  // Output editor's own `minHeight` prop below (see that instance's own comment) can floor its
  // *rendered* height at exactly `availableHeight` minus however tall that chrome actually is,
  // matching Input/the sidebar precisely instead of approximately. `useLayoutEffect`, not `useEffect`
  // — this measurement must land before the browser paints, or the very first frame would floor
  // the editor too tall by whatever this chrome's own height turns out to be. Recomputed whenever
  // the info row's own presence toggles (`output !== null`), since that's the only thing that
  // changes this chrome's total height in practice.
  const outputChromeRef = useRef<HTMLDivElement | null>(null);
  const [outputChromeHeight, setOutputChromeHeight] = useState(0);

  useLayoutEffect(() => {
    const node = outputChromeRef.current;
    if (!node) {
      return;
    }
    setOutputChromeHeight(node.getBoundingClientRect().height);
  }, [output !== null]);

  const runAction = useCallback(
    async (which: 'primary' | 'secondary', runSubmit: (input: string, minify: boolean) => Promise<DevUtilsResponse>) => {
      setSavingAction(which);
      try {
        const result = await runSubmit(input, minify);
        onErrorChange(null);
        onOutputChange(result.output);
      } catch (submitError) {
        onOutputChange(null);
        const message = submitError instanceof Error ? submitError.message : String(submitError);
        onErrorChange(buildDevUtilError(input, inputFormat === 'json', message));
      } finally {
        setSavingAction(null);
      }
    },
    [input, minify, onOutputChange, onErrorChange, inputFormat]
  );

  const handleSubmit = useCallback(() => runAction('primary', onSubmit), [runAction, onSubmit]);

  const handleSecondarySubmit = useCallback(() => {
    if (!secondaryAction) {
      return;
    }
    runAction('secondary', secondaryAction.onSubmit);
  }, [runAction, secondaryAction]);

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
    showSuccess(`Downloaded ${downloadFileName}`);
  }, [output, downloadFileName, showSuccess]);

  return (
    // `alignItems: 'flex-start'`, not the flexbox default `'stretch'` — Input carries its own
    // explicit `height: availableHeight`; Output instead carries `minHeight: availableHeight` (a
    // floor, not a fixed size — see this component's own doc comment for why Output needs its own
    // floor at all: it must never look shorter than Input for a short response, but still needs to
    // grow taller than that for a long one, up to GROWABLE_PANEL_MAX_HEIGHT). `flex-start` keeps each
    // card's own sizing fully self-contained — under the default `stretch`, cross-item resizing
    // would *probably* land on the same end result here (the row's cross size ends up the max of
    // both items' hypothetical sizes either way), but only by relying on a genuinely more subtle
    // mechanism (stretch's own per-flex-line cross-size computation, which gets more to reason
    // about once this row can wrap to two lines on a narrow viewport) — `flex-start` plus each
    // Paper's own explicit `height`/`minHeight` gets the identical result without depending on
    // that, so each card's rendered height is a direct function of its own sx alone.
    // `position: 'relative'` anchors the resize handle below, which overlays the shared border
    // between the two Papers rather than sitting between them as its own flex item — see
    // PanelResizeHandle's own comment. No `gap` at all: the two Papers touch directly.
    <Box ref={rowRef} sx={{ position: 'relative', display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start' }}>
      <Paper
        variant="outlined"
        sx={{
          // A user-draggable split, not a fixed 1:1 flex share. `flexShrink`/`flexGrow` stay
          // enabled (not `0 0 ...`) so a narrow viewport that wraps this card onto its own line
          // still grows it to fill that line's full width, same as the original plain `flex: 1`
          // did — only the *side-by-side* case is actually governed by `splitPercent`. Maximized
          // (either panel), this Paper instead takes the full row width regardless of
          // `splitPercent` — see `maximizedPanel`'s own comment. `display: 'none'`, not
          // conditional rendering, when *Output* is the maximized one — keeps this Paper's own
          // CodeMirror instance mounted (preserving its cursor/scroll/undo state) rather than
          // tearing it down every time the admin maximizes/restores the other side.
          flex: maximizedPanel === 'input' ? '1 1 100%' : `1 1 ${splitPercent}%`,
          minWidth: 320,
          height: availableHeight,
          display: maximizedPanel === 'output' ? 'none' : 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        <PanelHeader title="Input">
          <Button size="small" variant="outlined" startIcon={<ContentPasteIcon fontSize="small" />} onClick={handlePaste}>
            Paste
          </Button>
          <SubmitButton
            saving={savingAction === 'primary'}
            label={actionLabel}
            startIcon={<PlayArrowIcon fontSize="small" />}
            onClick={handleSubmit}
            disabled={!input.trim() || savingAction === 'secondary'}
          />
          {secondaryAction && (
            <SubmitButton
              saving={savingAction === 'secondary'}
              label={secondaryAction.label}
              startIcon={<PlayArrowIcon fontSize="small" />}
              onClick={handleSecondarySubmit}
              disabled={!input.trim() || savingAction === 'primary'}
            />
          )}
          {supportsMinify && (
            <Button
              size="small"
              variant={minify ? 'contained' : 'outlined'}
              startIcon={<UnfoldLessIcon fontSize="small" />}
              onClick={() => setMinify(m => !m)}
              aria-pressed={minify}
            >
              Minify
            </Button>
          )}
          <Tooltip title={maximizedPanel === 'input' ? 'Restore split view' : 'Maximize Input'}>
            <IconButton size="small" onClick={toggleMaximizeInput}>
              {maximizedPanel === 'input' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </PanelHeader>

        {/* `position: 'relative'` + the CodeMirror instance's own `position: 'absolute', inset: 0`
            (via `style`) is deliberately *not* the same "flex: 1, minHeight: 0, height: '100%'"
            approach used elsewhere in this file — that combination was tried first here too and
            hit a real, reported regression (no scrollbar appeared for content overflowing the
            box's own width/height; it just kept growing/clipping instead). Root cause: CodeMirror's
            own `height="100%"` prop only sets `.cm-editor { height: 100% }` — for that percentage
            to resolve to a real pixel value, its *direct* parent (the plain `<div>`
            `@uiw/react-codemirror` itself renders, which receives this component's own `style`
            prop) must have a genuinely definite height, and relying on that div picking one up
            purely from ambient flex stretch/grow through this Box → Paper's own flex chain turned
            out not to reliably resolve one in practice. `position: 'absolute', inset: 0` sidesteps
            the whole question: it fills its containing block's *already fully laid-out* size
            directly, however that size was arrived at, with no percentage-resolution of its own to
            fail — the exact same "top: 0, bottom: 0 against a position: 'relative' ancestor" trick
            already used for the resize handle further down in this same file. `.cm-scroller`'s own
            `overflow-x: auto` (explicit) plus `overflow-y: auto` (which the CSS Overflow spec
            implies once only one axis is set to something other than `visible`) then reliably
            engage once `.cm-editor` itself has a real, definite size to be measured against. No
            padding on this wrapping Box anymore — `editorChromeTheme`'s own `.cm-editor` padding
            provides the same 16px inset directly on the editor now. */}
        <Box sx={{ position: 'relative', flex: 1, minHeight: 0 }}>
          <CodeMirror
            value={input}
            onChange={value => onInputChange(value)}
            placeholder={inputPlaceholder}
            theme="light"
            extensions={[editorChromeTheme, ...inputExtensions]}
            height="100%"
            style={{ position: 'absolute', inset: 0 }}
          />
        </Box>
      </Paper>

      <Paper
        variant="outlined"
        // `minHeight`, not `height` — Output must never look shorter than Input/the sidebar for a
        // short response, but still needs to grow taller than that for a long one (the whole
        // point of the earlier partial revert). A floor, not a fixed size: short content floors at
        // availableHeight via the Output editor's own explicit `minHeight` prop below (a real,
        // measured value — see `outputChromeHeight`'s own comment for why this Paper's own
        // `minHeight` alone wasn't a reliable enough mechanism on its own); long content just grows
        // the Paper past it instead (min-height puts no ceiling on that), scrolling internally only
        // past GROWABLE_PANEL_MAX_HEIGHT (via the Output editor's own `maxHeight` prop). Maximized
        // (either panel), this Paper takes the full row width regardless of `splitPercent` — see
        // `usePanelMaximize`'s own comment; `display: 'none'`, not conditional rendering, when
        // *Input* is the maximized one, for the same "keep CodeMirror mounted" reason Input's own
        // Paper documents.
        sx={{
          flex: maximizedPanel === 'output' ? '1 1 100%' : `1 1 ${100 - splitPercent}%`,
          minWidth: 320,
          minHeight: availableHeight,
          display: maximizedPanel === 'input' ? 'none' : 'flex',
          flexDirection: 'column',
        }}
      >
        <Box ref={outputChromeRef}>
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
              {/* span wrapper — MUI requires one around a disabled button for the Tooltip to
                  still attach its listeners */}
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

          {output !== null && (
            <Stack
              direction="row"
              alignItems="center"
              sx={{ px: 2, py: 1, borderBottom: 1, borderColor: OUTPUT_LINE_COLOR, bgcolor: OUTPUT_INFO_BG }}
            >
              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                <Box component="span" sx={{ color: OUTPUT_LANGUAGE_INFO[outputLanguage].color, fontWeight: 700 }}>
                  {OUTPUT_LANGUAGE_INFO[outputLanguage].label}
                </Box>
                <Box component="span" sx={{ color: OUTPUT_FILENAME_COLOR }}>
                  {' | '}
                  {downloadFileName}
                </Box>
              </Typography>
            </Stack>
          )}
        </Box>

        {error !== null ? (
          <Box sx={{ p: 2, flex: 1, minHeight: 0, maxHeight: GROWABLE_PANEL_MAX_HEIGHT, overflow: 'auto', bgcolor: OUTPUT_BG_LIGHT }}>
            <Stack
              direction="row"
              spacing={1.5}
              sx={{
                p: 2,
                borderRadius: 1,
                border: '1px solid',
                borderColor: OUTPUT_ERROR_COLOR,
                bgcolor: alpha(OUTPUT_ERROR_COLOR, 0.08),
              }}
            >
              <ErrorOutlineIcon fontSize="small" sx={{ color: OUTPUT_ERROR_COLOR, mt: '2px' }} />
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="subtitle2" fontWeight={700} sx={{ color: OUTPUT_ERROR_COLOR }}>
                  {error.headline}
                </Typography>
                <Typography
                  component="pre"
                  sx={{
                    m: 0,
                    mt: 0.5,
                    color: 'grey.800',
                    fontFamily: 'monospace',
                    fontSize: '0.8rem',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                  }}
                >
                  {error.detail}
                </Typography>
              </Box>
            </Stack>
          </Box>
        ) : output !== null ? (
          // No wrapping Box needed here (unlike every other branch) — CodeMirror's own `style`
          // prop lands directly on the outer element it renders. `minHeight` is passed explicitly
          // (`availableHeight` minus `outputChromeHeight`, the header+info row's own real measured
          // height above) rather than left to an ambient `flex: 1` fill — a first cut relied on
          // exactly that ambient fill and it was a real, reported regression (this box's own
          // min-height didn't actually match Input/the sidebar). Passing `minHeight` directly to
          // CodeMirror is deterministic instead: the editor's own dimension theme sets it straight
          // on `.cm-editor`, no reliance on how flex-grow happens to distribute this Paper's free
          // space. `maxHeight` (not an external `sx.maxHeight` + `overflow: 'auto'`) is what caps
          // growth past GROWABLE_PANEL_MAX_HEIGHT — together, `minHeight`/`maxHeight` are the exact same
          // "floor, then hard cap" shape the Paper's own `minHeight: availableHeight` above
          // establishes, just enforced directly by the editor instead of inferred from its
          // surrounding flex layout. `style={{flex: 1, minHeight: 0}}` still lets this element grow
          // past its own `minHeight` prop when content genuinely needs more room (a `min-height`
          // prop is a floor only, never a ceiling), matching the Paper's own ability to grow past
          // its floor too. `readOnly` + `editable={false}` together give a fully read-only view
          // that still allows native text selection (for a manual copy, alongside the explicit
          // Copy button above) — `editable` alone would still render an editable cursor/caret
          // despite `readOnly` blocking actual mutation.
          <CodeMirror
            value={output}
            readOnly
            editable={false}
            theme={vscodeDark}
            extensions={[editorChromeTheme, ...outputExtensions]}
            minHeight={`${Math.max(0, availableHeight - outputChromeHeight)}px`}
            maxHeight={`${GROWABLE_PANEL_MAX_HEIGHT}px`}
            style={{ flex: 1, minHeight: 0 }}
          />
        ) : (
          <Stack
            spacing={1.5}
            alignItems="center"
            justifyContent="center"
            sx={{ p: 2, flex: 1, minHeight: 0, bgcolor: OUTPUT_BG_LIGHT }}
          >
            <DownloadIcon sx={{ fontSize: 40, color: 'grey.400' }} />
            <Typography variant="body2" sx={{ color: 'grey.600' }}>
              Output will appear here.
            </Typography>
          </Stack>
        )}
      </Paper>

      {/* Extracted into the shared PanelResizeHandle — see that component's own doc comment for
          why it's overlaid on the shared border between the two Papers (`position: 'absolute'`,
          not a flex item), why it renders no visible line at rest, and why it's hidden below `md`/
          whenever either panel is maximized. */}
      <PanelResizeHandle
        ariaLabel="Resize Input/Output panels"
        splitPercent={splitPercent}
        minPercent={minSplitPercent}
        maxPercent={maxSplitPercent}
        resizing={resizing}
        hidden={maximizedPanel !== null}
        onPointerDown={handleResizePointerDown}
        onPointerMove={handleResizePointerMove}
        onPointerUp={handleResizePointerUp}
        onDoubleClick={handleResizeDoubleClick}
        onKeyDown={handleResizeKeyDown}
      />
    </Box>
  );
}
