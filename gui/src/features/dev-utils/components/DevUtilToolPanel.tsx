import { useCallback, useState } from 'react';
import {
  Box,
  Button,
  IconButton,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import ContentPasteIcon from '@mui/icons-material/ContentPasteOutlined';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import PlayArrowIcon from '@mui/icons-material/PlayArrowOutlined';
import DownloadIcon from '@mui/icons-material/DownloadOutlined';
import UnfoldLessIcon from '@mui/icons-material/UnfoldLessOutlined';
// Already an outline-style glyph under its own distinct name (not the "Outlined" suffix
// convention every other icon above uses) — MUI ships "Error" (filled) and "ErrorOutline" as two
// separately named icons, not a base/Outlined pair, so there's no further outlined variant to
// switch to here.
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import SubmitButton from '@shared/components/SubmitButton';
import { useNotification } from '@shared/contexts/NotificationContext';
import { DevUtilsResponse } from '../types';
import { buildDevUtilError, DevUtilError } from '../utils/errorFormatting';
import { OUTPUT_LANGUAGE_INFO, OutputLanguage } from '../config/outputLanguages';

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
  /** What format the *input* box holds. Only ever actually branched on for the literal `'json'`
   * (picks `buildDevUtilError`'s client-side `JSON.parse` fast path); every other value just takes
   * that function's own doc comment for exactly which operations' backend can genuinely reject
   * their input (and therefore ever actually populate its fallback path). */
  inputFormat: 'json' | 'yaml' | 'html' | 'css' | 'less' | 'scss' | 'js' | 'erb' | 'xml' | 'csv' | 'sql' | 'php' | 'text';
  /** Prism language for the output syntax highlighter — also the key into
   * `config/outputLanguages.ts#OUTPUT_LANGUAGE_INFO` for this panel's own info-row badge. */
  outputLanguage: OutputLanguage;
  /** Whether this tool exposes a minify checkbox at all — false only for JSON→YAML, which has no
   * minify concept (see devUtilsApi.jsonToYaml's own comment). */
  supportsMinify: boolean;
  /** Filename offered by the Output panel's Download button, e.g. "formatted.json". */
  downloadFileName: string;
  onSubmit: (input: string, minify: boolean) => Promise<DevUtilsResponse>;
}

// The Output panel's background is state-driven, per request — white by default (no result yet,
// or a failed submit), switching to vscDarkPlus's own dark background
// (react-syntax-highlighter/dist/esm/styles/prism/vsc-dark-plus.js) only once a real result is
// showing. Both are fixed literals, not theme tokens (`background.paper` etc.) — this box's own
// color scheme is deliberately independent of the app's light/dark mode toggle, the same way a
// code editor's own theme doesn't follow the surrounding app's chrome.
const OUTPUT_BG_DARK = '#1e1e1e';
const OUTPUT_BG_LIGHT = '#ffffff';
// The light theme's own error red (`shared/constants/colors.ts`'s BRAND_COLORS.light.error) — used
// literally rather than the theme's own `error.main` token, since that token swaps to a brighter
// red tuned for a dark surface once the app is in dark mode, which would look wrong against this
// panel's always-white error background.
const OUTPUT_ERROR_COLOR = '#cf222e';
// A shade lighter than OUTPUT_BG_DARK, per request ("use bgColor black also, less black than the
// content") — VS Code Dark+'s own toolbar/sidebar tone, distinguishing the info row from the code
// content below it without breaking from the dark, theme-independent look this panel already has.
const OUTPUT_INFO_BG = '#252526';
// A muted, de-emphasized grey for the file-name value and the line-number gutter, per request —
// neither is the focused content (the response itself is), so both stay visually secondary rather
// than reading as bright/prominent text. Still light enough to stay legible against the dark
// backgrounds, just clearly dimmer than the response text itself or the file-type badge colors.
const OUTPUT_FILENAME_COLOR = '#6e7681';
const OUTPUT_LINE_NUMBER_COLOR = '#6e7681';
// A fixed mid-dark grey (VS Code's own default border/separator tone) used for the info row's own
// border-bottom — deliberately not the theme's `divider` token, which is a translucent black/white
// that barely shows up against a hardcoded dark background (or, in one case, disappears into it
// depending on the app's own light/dark mode), the same "fixed literal, not a theme token"
// reasoning as this panel's other colors. Reads as a normal dark rule against the white content
// states too.
const OUTPUT_LINE_COLOR = '#3c3c3c';

// Shared cap for the Output panel's content area (error box / placeholder / syntax-highlighted
// result alike) — keeps it growing with content up to a reasonable height, then scrolling
// internally, roughly matching the Input TextField's own minRows/maxRows auto-grow range.
const OUTPUT_MAX_HEIGHT = 800;
// The empty placeholder's own starting height — deliberately taller than a "just enough for the
// icon + one line of text" box would need, so it roughly matches the Input side's own starting
// height (`minRows={20}` below, at this panel's line-height/padding) rather than visibly
// shrinking the whole Output card the moment there's no result yet.
const OUTPUT_EMPTY_MIN_HEIGHT = 425;

function downloadTextFile(fileName: string, content: string): void {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

/** The Input/Output split panel every /dev-utils tool renders — each side is its own bordered
 * card, per request, with its action buttons on the same line as its own title ("Input   [Paste]
 * [<actionLabel>] [Minify]" / "Output   [Copy] [Download]") rather than a separate toolbar row.
 * Minify is its own toggle button (`variant` swaps outlined/contained to show pressed state),
 * not a `Checkbox`, per a follow-up request — positioned after the action button, and omitted
 * entirely (not just disabled) when the operation doesn't support it. Each tab configures this
 * for its own operation rather than this component knowing about any specific one.
 *
 * <p>`input`, `output`, and `error` are all controlled props, not local state — lifted up to
 * `DevUtilsPage.tsx` once that page's own headline row needed Sample/Clear buttons able to
 * set/reset them directly (this panel's own Paste button and the `TextField`'s typing both just
 * call `onInputChange` now, the same as that page's own callers; a submit calls `onOutputChange`
 * on success or `onErrorChange` on failure instead of local setters). Every other piece of state
 * here (`minify`/`saving`/`copied`) stays local — `DevUtilsPage.tsx` still remounts this component
 * on tool switch (`key={...}`) to reset those, independently of the parent's own `input`/`output`/
 * `error` reset.
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
 * for both the empty placeholder and an `error`, switching to black (`OUTPUT_BG_DARK`, the syntax
 * highlighter's own dark theme) only once `output` actually holds a real result. This deliberately
 * reintroduces the white → black transition an earlier fix had removed (see git history/
 * `docs/CHANGELOG.md` around that fix if picking through this box's own color history) — that
 * transition is the explicit ask here, not an oversight.
 *
 * <p>Between the header row and the content area, a small info row shows a single
 * "{@code <TYPE> | <filename>}" line — no "File type:"/"File name:" labels — but **only once
 * `output` actually holds a real result**, per a follow-up request; it renders `null` for the
 * empty-placeholder and `error` states, unlike an earlier version of this row that showed it
 * unconditionally across all three (don't reintroduce that without confirming it's wanted again).
 * Both values are already known statically per operation (`outputLanguage`/`downloadFileName`), so
 * nothing here is actually derived from the response itself — only the decision of *whether* to
 * show them is now response-gated. `config/outputLanguages.ts#OUTPUT_LANGUAGE_INFO` maps the
 * Prism language id (`outputLanguage`) each operation already passes to a human label
 * (`JSON`/`YAML`/`HTML`/etc.) and a per-language badge color, one shared map so the two can never
 * drift out of sync with each other (see that file's own doc comment — it used to be two
 * independently-maintained `Record<string, string>`s here). Its background is a fixed
 * `OUTPUT_INFO_BG` (a shade lighter than `OUTPUT_BG_DARK` — always dark, unlike the content area
 * below it, which still switches white/black by state) rather than a theme token, same
 * "independent of the app's light/dark toggle" reasoning as this panel's other colors. The
 * `<TYPE>` segment is colored per language (common language-badge convention) and bold; the `|`
 * separator and the filename both use the muted `OUTPUT_FILENAME_COLOR` grey, since neither is the
 * focused content — the response itself is. All
 * three segments are plain `<Box component="span">`s inside one `Typography`, not separate
 * flex-positioned elements — a simpler one-line rendering superseded an earlier attempt at
 * horizontally aligning the type/filename with the line-number/response columns beneath them (that
 * column-alignment scheme, plus a full-height vertical gutter-divider line and a baseline-mismatch
 * fix it needed, were all tried in earlier passes and then explicitly simplified away per a direct
 * request for this plainer template — don't reintroduce that alignment complexity without
 * confirming it's wanted again). The syntax highlighter itself still has `showLineNumbers` (own
 * `OUTPUT_LINE_NUMBER_COLOR`) — only meaningful for the actual `output` branch, not the
 * error/placeholder ones, since neither of those renders line-oriented content.
 *
 * <p>The info row's own border-bottom uses a fixed `OUTPUT_LINE_COLOR` rather than the theme's
 * `divider` token — `divider` is a translucent black/white overlay tuned for the app's own
 * background, which barely shows (or vanishes, depending on light/dark app mode) against this
 * panel's hardcoded dark background. */
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
}: DevUtilToolPanelProps): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [minify, setMinify] = useState(false);
  const [saving, setSaving] = useState(false);
  const [copied, setCopied] = useState(false);

  const handleSubmit = useCallback(async () => {
    setSaving(true);
    try {
      const result = await onSubmit(input, minify);
      onErrorChange(null);
      onOutputChange(result.output);
    } catch (submitError) {
      onOutputChange(null);
      const message = submitError instanceof Error ? submitError.message : String(submitError);
      onErrorChange(buildDevUtilError(input, inputFormat === 'json', message));
    } finally {
      setSaving(false);
    }
  }, [input, minify, onSubmit, onOutputChange, onErrorChange, inputFormat]);

  const handlePaste = useCallback(async () => {
    try {
      const text = await navigator.clipboard.readText();
      onInputChange(text);
    } catch {
      showError('Could not read from the clipboard — check your browser permissions.');
    }
  }, [onInputChange, showError]);

  const handleCopy = useCallback(async () => {
    if (output === null) return;
    await navigator.clipboard.writeText(output);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }, [output]);

  const handleDownload = useCallback(() => {
    if (output === null) return;
    downloadTextFile(downloadFileName, output);
    showSuccess(`Downloaded ${downloadFileName}`);
  }, [output, downloadFileName, showSuccess]);

  return (
    // Deliberately default align-items ('stretch') here, not 'flex-start' — it makes both Paper
    // cards match the height of whichever one has more content (taller natural height), so Input
    // and Output always end up the same overall height, driven by whichever has more lines. Each
    // side's own content-area child below carries `flex: 1` so it's the *visible* content box
    // (not just the Paper's own blank background) that actually fills the extra stretched height.
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
      <Paper variant="outlined" sx={{ flex: 1, minWidth: 320, display: 'flex', flexDirection: 'column' }}>
        <Stack
          direction="row"
          alignItems="center"
          justifyContent="space-between"
          sx={{ px: 2, py: 1, borderBottom: 1, borderColor: 'divider' }}
        >
          <Typography variant="subtitle2" fontWeight={700} sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}>
            Input
          </Typography>
          <Stack direction="row" spacing={1}>
            <Button size="small" variant="outlined" startIcon={<ContentPasteIcon fontSize="small" />} onClick={handlePaste}>
              Paste
            </Button>
            <SubmitButton
              saving={saving}
              label={actionLabel}
              startIcon={<PlayArrowIcon fontSize="small" />}
              onClick={handleSubmit}
              disabled={!input.trim()}
            />
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
          </Stack>
        </Stack>

        <Box sx={{ p: 2, flex: 1, minHeight: 0 }}>
          <TextField
            placeholder={inputPlaceholder}
            multiline
            minRows={20}
            maxRows={32}
            fullWidth
            value={input}
            onChange={e => onInputChange(e.target.value)}
            // Hides the outlined variant's own border — without this, the TextField's box sits
            // visibly nested inside this Input card's own Paper border, reading as "a box inside
            // a box." All three states are targeted explicitly (default/hover/focused), not just
            // the base `.MuiOutlinedInput-notchedOutline` selector alone — MUI's own hover/focus
            // rules for that same element are more specific (extra pseudo-class), so an override
            // scoped to only the base selector would silently lose on hover/focus, the identical
            // specificity gotcha just fixed on the sidebar's selected-item background.
            sx={{
              '& .MuiOutlinedInput-root': {
                '& .MuiOutlinedInput-notchedOutline': { border: 'none' },
                '&:hover .MuiOutlinedInput-notchedOutline': { border: 'none' },
                '&.Mui-focused .MuiOutlinedInput-notchedOutline': { border: 'none' },
              },
            }}
          />
        </Box>
      </Paper>

      <Paper variant="outlined" sx={{ flex: 1, minWidth: 320, display: 'flex', flexDirection: 'column' }}>
        <Stack
          direction="row"
          alignItems="center"
          justifyContent="space-between"
          sx={{ px: 2, py: 1, borderBottom: 1, borderColor: 'divider' }}
        >
          <Typography variant="subtitle2" fontWeight={700} sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}>
            Output
          </Typography>
          <Stack direction="row" spacing={1}>
            <Button
              size="small"
              variant="outlined"
              startIcon={<ContentCopyIcon fontSize="small" />}
              onClick={handleCopy}
              disabled={output === null}
            >
              {copied ? 'Copied!' : 'Copy'}
            </Button>
            <Tooltip title="Download">
              {/* span wrapper — MUI requires one around a disabled button for the Tooltip to still
                  attach its listeners */}
              <span>
                <IconButton size="small" onClick={handleDownload} disabled={output === null}>
                  <DownloadIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          </Stack>
        </Stack>

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

        {error !== null ? (
          <Box sx={{ p: 2, flex: 1, minHeight: 0, maxHeight: OUTPUT_MAX_HEIGHT, overflow: 'auto', bgcolor: OUTPUT_BG_LIGHT }}>
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
          // react-syntax-highlighter tags every line-number span with a `comment` className
          // alongside `linenumber` (highlight.js's own createLineElement) so it can reuse the
          // theme's comment-token color as a sensible default — but its style-merge order then
          // re-applies that theme color *after* `lineNumberStyle`'s own `color`, silently
          // clobbering it (confirmed by inspecting react-syntax-highlighter's own createElement/
          // createStyleObject source, not guessed): `stylesheet['comment']` — vscDarkPlus's own
          // `{ color: '#6a9955' }` — is spread on top of `lineNumberStyle`'s merged style, so
          // `lineNumberStyle.color` never actually reaches the DOM. A `!important` CSS rule is the
          // only thing that can still win here, since CSS's own cascade ranks any `!important`
          // declaration above a plain (non-`!important`) inline style regardless of origin —
          // targeting the `.react-syntax-highlighter-line-number` class this same library adds
          // specifically for cases like this.
          <Box
            sx={{
              flex: 1,
              minHeight: 0,
              maxHeight: OUTPUT_MAX_HEIGHT,
              display: 'flex',
              flexDirection: 'column',
              '& .react-syntax-highlighter-line-number': { color: `${OUTPUT_LINE_NUMBER_COLOR} !important` },
            }}
          >
            <SyntaxHighlighter
              language={outputLanguage}
              style={vscDarkPlus}
              showLineNumbers
              lineNumberStyle={{ minWidth: '2.5em', paddingRight: '0.25em', userSelect: 'none', marginRight: '16px' }}
              customStyle={{
                margin: 0,
                borderRadius: 0,
                fontSize: '0.8rem',
                padding: '16px',
                flex: 1,
                minHeight: 0,
                overflow: 'auto',
                background: OUTPUT_BG_DARK,
              }}
            >
              {output}
            </SyntaxHighlighter>
          </Box>
        ) : (
          <Stack
            spacing={1.5}
            alignItems="center"
            justifyContent="center"
            sx={{ p: 2, flex: 1, minHeight: OUTPUT_EMPTY_MIN_HEIGHT, maxHeight: OUTPUT_MAX_HEIGHT, bgcolor: OUTPUT_BG_LIGHT }}
          >
            <DownloadIcon sx={{ fontSize: 40, color: 'grey.400' }} />
            <Typography variant="body2" sx={{ color: 'grey.600' }}>
              Output will appear here.
            </Typography>
          </Stack>
        )}
      </Paper>
    </Box>
  );
}
