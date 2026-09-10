import { useCallback, useRef, useState, DragEvent as ReactDragEvent, ChangeEvent, ClipboardEvent as ReactClipboardEvent } from 'react';
import { Box, Button, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUploadOutlined';
import ContentPasteIcon from '@mui/icons-material/ContentPasteOutlined';
import BrokenImageIcon from '@mui/icons-material/BrokenImageOutlined';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import DownloadIcon from '@mui/icons-material/DownloadOutlined';
import CheckIcon from '@mui/icons-material/CheckOutlined';
import OpenInFullIcon from '@mui/icons-material/OpenInFullOutlined';
import CloseFullscreenIcon from '@mui/icons-material/CloseFullscreenOutlined';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useResizableSplit } from '../hooks/useResizableSplit';
import { usePanelMaximize } from '../hooks/usePanelMaximize';
import PanelHeader from './PanelHeader';
import PanelResizeHandle from './PanelResizeHandle';
import { useCopyFeedback } from '../hooks/useCopyFeedback';
import { HIDDEN_TEXT_FIELD_OUTLINE_SX } from '../utils/textFieldStyles';

interface Base64ImagePanelProps {
  /** Controlled — same lifted `input` state `DevUtilsPage.tsx` already threads into
   * `DevUtilToolPanel` for every other operation, doubling here as the pasted-or-converted Data
   * URL text itself (the "ImageUrl box" from the request) — so the headline row's Sample/Clear
   * buttons keep working unchanged even though the rest of this panel's own layout doesn't. */
  input: string;
  onInputChange: (value: string) => void;
  inputPlaceholder: string;
  /** Height (px) computed by `DevUtilsPage.tsx` from the actual viewport — the same value the
   * shared `DevUtilToolPanel`'s Input card and the sidebar both size themselves to. Applied as a
   * fixed `height` to **both** the left column (Upload + Image Data URL together) and the Preview
   * card, per a follow-up request — the left column used to keep its own natural, content-driven
   * height, unmatched to the sidebar; see this component's own doc comment for the fixed-vs-floor
   * reasoning (a single bounded image, or a bounded drop-zone/text box, never needs to grow past
   * this the way `RegExpTesterPanel.tsx`'s match list or `TextDiffPanel.tsx`'s Diff view can, so
   * there's no floor-vs-fixed distinction to make here — unlike those two, `height`, not
   * `minHeight`, everywhere in this panel). */
  availableHeight: number;
}

// The classic 2-gradient checkerboard trick (a fixed light/dark grey pair, not a theme token —
// the same "independent of the app's own light/dark toggle" reasoning DevUtilToolPanel.tsx's own
// OUTPUT_BG_LIGHT/OUTPUT_INFO_BG constants already establish for this panel's sibling) — the
// standard way image tools (Photoshop, GIMP, browser DevTools' own image preview, etc.) render a
// preview surface that might have transparency, so a PNG/WebP/SVG's own alpha channel is visible
// as "you can see the pattern through it" rather than blending invisibly into a flat background.
const CHECKERBOARD_LIGHT = '#ffffff';
const CHECKERBOARD_DARK = '#e0e0e0';
const CHECKERBOARD_TILE_PX = 16;
const CHECKERBOARD_BACKGROUND = {
  backgroundColor: CHECKERBOARD_LIGHT,
  backgroundImage: `repeating-conic-gradient(${CHECKERBOARD_DARK} 0% 25%, ${CHECKERBOARD_LIGHT} 0% 50%)`,
  backgroundSize: `${CHECKERBOARD_TILE_PX * 2}px ${CHECKERBOARD_TILE_PX * 2}px`,
};

// A generous but finite cap, purely a client-side UX safeguard (unlike every other operation's
// own MAX_INPUT_LENGTH, there is no backend round trip here at all to enforce one) — a very large
// image would otherwise base64-encode into a multi-megabyte string sitting in React state and a
// plain <textarea>, which gets sluggish well before this limit.
const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

// Restricted to a fixed, explicit allow-list per request — deliberately narrower than the more
// permissive `file.type.startsWith('image/')` check this component used before (which would also
// have accepted e.g. BMP/TIFF/AVIF/ICO). Doubles as the `accept` attribute's own value list and
// the Download button's own extension lookup, so all three (validation, the file picker's own
// filter, and what a downloaded file gets named) can never drift out of sync with each other.
const ALLOWED_IMAGE_TYPES: Record<string, string> = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/gif': 'gif',
  'image/webp': 'webp',
  'image/svg+xml': 'svg',
};
const ALLOWED_IMAGE_TYPES_LABEL = 'PNG, JPG, GIF, WebP, SVG';
const FILE_INPUT_ACCEPT = Object.keys(ALLOWED_IMAGE_TYPES).join(',');

function formatBytes(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// Parses a `data:<mime>;base64,<data>` string apart — used both to decide whether Download should
// be enabled at all and to actually build the Blob it downloads. Deliberately returns `null`
// (rather than throwing) for anything that isn't this exact shape, e.g. a plain https:// URL
// pasted into the Data URL box instead of a real Data URL — Download has nothing to decode in
// that case, so it just stays disabled instead of attempting a doomed `atob` call.
function parseDataUrl(value: string): { mime: string; base64: string } | null {
  const match = /^data:([^;,]+);base64,([\s\S]+)$/.exec(value.trim());
  return match ? { mime: match[1], base64: match[2] } : null;
}

function dataUrlToBlob(value: string): Blob | null {
  const parsed = parseDataUrl(value);
  if (!parsed) return null;
  try {
    const binary = atob(parsed.base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return new Blob([bytes], { type: parsed.mime });
  } catch {
    // Malformed base64 (e.g. a pasted Data URL truncated mid-copy) — same "nothing to download"
    // outcome as a non-Data-URL string, not a crash.
    return null;
  }
}

/**
 * Base64 Image's own bespoke Input/Output layout — a dedicated component, not a mode grafted onto
 * the shared `DevUtilToolPanel.tsx`, per direct request: this operation's input is a *file* (or a
 * pasted Data URL string), and its output is a rendered image preview, neither of which fits that
 * component's plain-text Input/Output editor design at all — the same "some operations need a
 * genuinely different layout" precedent `HashGeneratorPanel.tsx` already established.
 * `DevUtilsPage.tsx` renders this in place of `DevUtilToolPanel` for exactly this one operation.
 *
 * <p>**Entirely client-side — no `dev-utils-service` endpoint, no `onSubmit` call at all.**
 * Converting an uploaded file to a Data URL is exactly what {@link FileReader#readAsDataURL} does
 * natively in the browser; round-tripping the raw image bytes through the backend just to
 * base64-encode them (something the browser already does, instantly, for free) would only add
 * latency and payload size for a large image with zero benefit. This is a genuine, deliberate
 * exception to "every operation calls a backend endpoint" — see `config/operations.tsx`'s own
 * `base64-image` entry for how its unused `onSubmit`/`inputFormat`/etc. fields are documented.
 *
 * <p>Two Input boxes, per request: **Upload** (drag-and-drop, click-to-select, or pasting an
 * actual image from the clipboard — e.g. a screenshot — all converge on the same `handleFile`) and
 * **Image Data URL** (a plain multiline text box a Data URL string can also be pasted or typed
 * into directly) — both write to the same lifted `input` string, so any of those paths feeds the
 * same single source of truth. The **Preview** box (Output) is just `<img src={input}>` — it
 * re-renders automatically whenever `input` changes, with no separate "which path produced this"
 * state to track, plus its own Copy (the Data URL text)/Download (the decoded image file) actions.
 * An `onError`/`onLoad` pair on that `<img>` is what tells a malformed/incomplete pasted Data URL
 * apart from a genuinely loadable image, since there's no server-side validation step to catch
 * that instead.
 *
 * <p>**Two independent resizable splits, plus a 3-way maximize, per a follow-up audit request**
 * (the same "recheck against `DevUtilToolPanel.tsx`'s own autogrow/minHeight/resizable-maximize
 * checks" pass `RegExpTesterPanel.tsx`/`TextDiffPanel.tsx` already went through — this panel was
 * deliberately left unchanged in that first pass since its Preview card already had the
 * `availableHeight` treatment, but a direct follow-up asked to go further):
 * <ul>
 *   <li>A **horizontal** split (`hooks/useResizableSplit.ts`'s default `orientation`) between the
 *       whole left column (Upload + Image Data URL together) and Preview — the same mechanism
 *       `RegExpTesterPanel.tsx`/`TextDiffPanel.tsx` already use for their own left-column-vs-
 *       growable-side split, persisted under its own `localStorage` key.
 *   <li>A **vertical** split (`orientation: 'vertical'`, the first consumer of that option) inside
 *       the left column itself, between Upload and Image Data URL — defaults to 30%/70% per
 *       request, since the drop-zone rarely needs as much room as a long Data URL string does.
 *       This is what let both cards' own `height`s become fully proportional (`flex-basis`) to
 *       the left column's fixed `availableHeight`, rather than each sizing to its own natural
 *       content the way this panel did before.
 * </ul>
 * `maximizedPanel` is a 3-way exclusive toggle (`'upload' | 'dataUrl' | 'preview' | null`, via
 * `hooks/usePanelMaximize.ts`) rather than the 2-way toggle every other panel in this feature
 * uses — this is the first operation with **3** panels instead of 2, so maximizing any one of
 * them hides the *other two* entirely (not just "the other side of one split"): maximizing
 * Preview hides the whole left column (both splits become moot, so both resize handles hide
 * too); maximizing Upload or Image Data URL hides Preview *and* the sibling card within the left
 * column. Width-only, same convention every other panel's maximize already establishes — it
 * never changes the fixed `availableHeight` any of these cards render at.
 *
 * <p>**Image Data URL deliberately stayed a plain `TextField`, not a CodeMirror editor** —
 * unlike `RegExpTesterPanel.tsx`'s Test String (converted to CodeMirror in the same audit pass
 * this panel's own resizable split was added in), this box has a real, already-working
 * image-paste-detection feature (`handlePaste` below, checking the clipboard for an actual image
 * before falling back to plain text) built directly on the browser's own native `<textarea>`
 * paste semantics — a plain DOM `preventDefault()` on the same event a native textarea would
 * otherwise use to insert the pasted text. CodeMirror 6 doesn't defer to that native behavior at
 * all (it manages its own document model via its own event handling, not the browser's built-in
 * contenteditable/textarea default action), so reproducing this exact interception would need
 * CodeMirror's own `EditorView.domEventHandlers` extension API instead of a plain React
 * `onPaste` prop — a real rewrite of already-working, non-trivial logic for no benefit relevant
 * to this follow-up's own ask (fill-height, not richer text editing). Filling the vertical split's
 * own flexible height instead reuses the older, `!important`-override `TextField` technique this
 * feature's `DevUtilToolPanel.tsx` itself used *before* it migrated onto CodeMirror (see that
 * component's own git history if picking through this) — proven to work for exactly this "fill an
 * ancestor sized by flex-grow" shape, just without CodeMirror's own event-handling trade-off.
 */
export default function Base64ImagePanel({
  input,
  onInputChange,
  inputPlaceholder,
  availableHeight,
}: Base64ImagePanelProps): JSX.Element {
  const { showError } = useNotification();
  const [dragActive, setDragActive] = useState(false);
  const [previewFailed, setPreviewFailed] = useState(false);
  // Keyed 'data-url'/'preview' so the two Copy buttons show independent "Copied!" feedback.
  const { copiedKey, copy } = useCopyFeedback();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { maximizedPanel, toggle: toggleMaximize } = usePanelMaximize<'upload' | 'dataUrl' | 'preview'>();
  const toggleMaximizeUpload = useCallback(() => toggleMaximize('upload'), [toggleMaximize]);
  const toggleMaximizeDataUrl = useCallback(() => toggleMaximize('dataUrl'), [toggleMaximize]);
  const toggleMaximizePreview = useCallback(() => toggleMaximize('preview'), [toggleMaximize]);

  // Horizontal: the left column (Upload + Image Data URL together) vs. Preview — the same shape
  // RegExpTesterPanel.tsx/TextDiffPanel.tsx already use for their own 2-side split.
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
  } = useResizableSplit({ storageKey: 'devUtilsBase64PanelHorizontalSplitPercent' });

  // Vertical: Upload vs. Image Data URL, stacked inside the left column — defaults 30%/70% per
  // request, the first consumer of useResizableSplit's own 'vertical' orientation.
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
    storageKey: 'devUtilsBase64PanelVerticalSplitPercent',
    orientation: 'vertical',
    defaultPercent: 30,
  });

  const handleFile = useCallback(
    (file: File) => {
      if (!(file.type in ALLOWED_IMAGE_TYPES)) {
        showError(`"${file.name}" isn't a supported image type — allowed: ${ALLOWED_IMAGE_TYPES_LABEL}.`);
        return;
      }
      if (file.size > MAX_FILE_SIZE_BYTES) {
        showError(`"${file.name}" is ${formatBytes(file.size)} — the limit is ${formatBytes(MAX_FILE_SIZE_BYTES)}.`);
        return;
      }
      const reader = new FileReader();
      reader.onload = () => {
        setPreviewFailed(false);
        onInputChange(String(reader.result));
      };
      reader.onerror = () => showError(`Could not read "${file.name}".`);
      reader.readAsDataURL(file);
    },
    [onInputChange, showError]
  );

  const handleFileInputChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) handleFile(file);
      // Reset so re-selecting the exact same file still fires this handler next time.
      e.target.value = '';
    },
    [handleFile]
  );

  const handleDrop = useCallback(
    (e: ReactDragEvent<HTMLDivElement>) => {
      e.preventDefault();
      setDragActive(false);
      const file = e.dataTransfer.files?.[0];
      if (file) handleFile(file);
    },
    [handleFile]
  );

  // Lets an actual image (e.g. a screenshot copied to the clipboard) be pasted directly into the
  // Data URL box, not just a Data URL *string* — checked first; when the clipboard holds no image,
  // this is a no-op and the browser's own default text-paste behavior (pasting a Data URL string)
  // proceeds exactly as before.
  const handlePaste = useCallback(
    // `HTMLDivElement`, not `HTMLTextAreaElement` — MUI's own `TextField`/`OutlinedInput` types
    // `onPaste` against the root element's event type regardless of the `multiline` prop, not the
    // rendered `<textarea>`'s; the event's `clipboardData` (the only thing read below) doesn't
    // depend on which element type this is anyway.
    (e: ReactClipboardEvent<HTMLDivElement>) => {
      const items = e.clipboardData?.items;
      if (!items) return;
      // Plain indexed loop, not for...of — DataTransferItemList's own TS typings don't guarantee
      // an iterator the way FileList's do, so a for...of here risks a downlevelIteration error
      // depending on this project's own tsconfig target.
      for (let i = 0; i < items.length; i++) {
        const item = items[i];
        if (item.type.startsWith('image/')) {
          const file = item.getAsFile();
          if (file) {
            e.preventDefault();
            handleFile(file);
          }
          return;
        }
      }
    },
    [handleFile]
  );

  // The explicit "Paste" button's own click handler — a real click is a user gesture, so
  // `navigator.clipboard.read()` (image-capable, unlike `readText()`) is available here the same
  // way it would be from a keyboard Ctrl+V; tries that first (an actual image on the clipboard
  // takes the same `handleFile` path a real upload does) and falls back to `readText()` for a
  // plain Data URL string, mirroring `handlePaste`'s own image-first-then-text priority above so
  // the button and native paste never disagree about which one wins when both are present.
  const handlePasteButtonClick = useCallback(async () => {
    try {
      if (navigator.clipboard.read) {
        const items = await navigator.clipboard.read();
        for (const item of items) {
          const imageType = item.types.find(type => type.startsWith('image/'));
          if (imageType) {
            const blob = await item.getType(imageType);
            const extension = ALLOWED_IMAGE_TYPES[imageType] ?? 'png';
            handleFile(new File([blob], `pasted.${extension}`, { type: imageType }));
            return;
          }
        }
      }
      const text = await navigator.clipboard.readText();
      if (text) {
        setPreviewFailed(false);
        onInputChange(text);
      }
    } catch {
      showError('Could not read from the clipboard — check your browser permissions.');
    }
  }, [handleFile, onInputChange, showError]);

  const handleCopyDataUrl = useCallback(() => {
    if (!input) return;
    copy(input, 'data-url');
  }, [input, copy]);

  const handleCopyPreview = useCallback(() => {
    if (!input) return;
    copy(input, 'preview');
  }, [input, copy]);

  const handleDownload = useCallback(() => {
    const blob = dataUrlToBlob(input);
    if (!blob) {
      showError('Could not download — this is not a valid image Data URL.');
      return;
    }
    const extension = ALLOWED_IMAGE_TYPES[blob.type] ?? 'bin';
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `image.${extension}`;
    link.click();
    URL.revokeObjectURL(url);
  }, [input, showError]);

  const canDownload = !previewFailed && parseDataUrl(input) !== null;

  const uploadHidden = maximizedPanel === 'dataUrl' || maximizedPanel === 'preview';
  const dataUrlHidden = maximizedPanel === 'upload' || maximizedPanel === 'preview';
  const leftColumnHidden = maximizedPanel === 'preview';
  const previewHidden = maximizedPanel === 'upload' || maximizedPanel === 'dataUrl';

  return (
    // `position: 'relative'` anchors the horizontal PanelResizeHandle; no `gap` — the left column
    // and Preview's own flex-basis percentages sum to 100%, mirroring every other split row in
    // this feature.
    <Box ref={rowRef} sx={{ position: 'relative', display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start' }}>
      <Box
        ref={columnRef}
        sx={{
          position: 'relative',
          flex: maximizedPanel === 'upload' || maximizedPanel === 'dataUrl' ? '1 1 100%' : `1 1 ${horizontalSplit}%`,
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
            flex: maximizedPanel === 'upload' ? '1 1 100%' : `1 1 ${verticalSplit}%`,
            minHeight: 0,
            display: uploadHidden ? 'none' : 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          <PanelHeader title="Upload Image">
            <Tooltip title={maximizedPanel === 'upload' ? 'Restore split view' : 'Maximize Upload Image'}>
              <IconButton size="small" onClick={toggleMaximizeUpload}>
                {maximizedPanel === 'upload' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </PanelHeader>
          <input
            ref={fileInputRef}
            type="file"
            accept={FILE_INPUT_ACCEPT}
            style={{ display: 'none' }}
            onChange={handleFileInputChange}
          />
          {/* No border at rest — per request, this used to draw its own dashed box nested inside
              this card's own Paper border, reading as "a box inside a box." A bgcolor shift on
              hover/drag (plus the cursor and the icon/text themselves) is enough affordance that
              this is clickable/droppable without a persistent border. `flex: 1` (a follow-up —
              this used to be a fixed `p: 4` block with no flex-grow, leaving blank space below it
              once this card's own height became proportional to `availableHeight` instead of
              shrink-to-fit) fills whatever height the vertical split/maximize gives this card,
              centering its content either way. */}
          <Box
            onClick={() => fileInputRef.current?.click()}
            onDragOver={e => {
              e.preventDefault();
              setDragActive(true);
            }}
            onDragLeave={() => setDragActive(false)}
            onDrop={handleDrop}
            sx={{
              flex: 1,
              minHeight: 0,
              m: 2,
              borderRadius: 1,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              textAlign: 'center',
              cursor: 'pointer',
              bgcolor: dragActive ? 'action.selected' : 'transparent',
              '&:hover': { bgcolor: 'action.hover' },
              transition: 'background-color 0.1s',
            }}
          >
            <CloudUploadIcon sx={{ fontSize: 40, color: 'grey.500' }} />
            <Typography variant="body2" fontWeight={600} sx={{ mt: 1 }}>
              Drop or select an image
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {ALLOWED_IMAGE_TYPES_LABEL} (up to {formatBytes(MAX_FILE_SIZE_BYTES)})
            </Typography>
          </Box>
        </Paper>

        <PanelResizeHandle
          ariaLabel="Resize Upload Image/Image Data URL panels"
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
            flex: maximizedPanel === 'dataUrl' ? '1 1 100%' : `1 1 ${100 - verticalSplit}%`,
            minHeight: 0,
            display: dataUrlHidden ? 'none' : 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          <PanelHeader title="Image Data URL">
            <Button
              size="small"
              variant="outlined"
              startIcon={<ContentPasteIcon fontSize="small" />}
              onClick={handlePasteButtonClick}
            >
              Paste
            </Button>
            <Tooltip title={copiedKey === 'data-url' ? 'Copied!' : 'Copy'}>
              <span>
                <IconButton size="small" onClick={handleCopyDataUrl} disabled={!input}>
                  {copiedKey === 'data-url' ? (
                    <CheckIcon fontSize="small" color="success" />
                  ) : (
                    <ContentCopyIcon fontSize="small" />
                  )}
                </IconButton>
              </span>
            </Tooltip>
            <Tooltip title={maximizedPanel === 'dataUrl' ? 'Restore split view' : 'Maximize Image Data URL'}>
              <IconButton size="small" onClick={toggleMaximizeDataUrl}>
                {maximizedPanel === 'dataUrl' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </PanelHeader>
          {/* Fills this card's own flex-grown height via the older `!important`-override
              TextField technique (see this component's own doc comment for why this box stayed a
              plain TextField instead of following RegExpTesterPanel.tsx's Test String onto
              CodeMirror) — `rows={1}` forces MUI off its own JS-driven auto-resize path so the
              CSS height override below can take full control. */}
          <Box sx={{ p: 2, flex: 1, minHeight: 0, display: 'flex' }}>
            <TextField
              value={input}
              onChange={e => {
                setPreviewFailed(false);
                onInputChange(e.target.value);
              }}
              onPaste={handlePaste}
              placeholder={inputPlaceholder}
              multiline
              rows={1}
              fullWidth
              sx={{
                flex: 1,
                display: 'flex',
                '& .MuiInputBase-root': { flex: 1, alignItems: 'flex-start' },
                '& .MuiInputBase-input': { fontFamily: 'monospace', fontSize: '0.75rem', wordBreak: 'break-all' },
                '& .MuiInputBase-inputMultiline': { height: '100% !important', overflow: 'auto !important' },
                ...HIDDEN_TEXT_FIELD_OUTLINE_SX,
              }}
            />
          </Box>
        </Paper>
      </Box>

      <PanelResizeHandle
        ariaLabel="Resize Upload/Image Data URL column and Preview panels"
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
          flex: maximizedPanel === 'preview' ? '1 1 100%' : `1 1 ${100 - horizontalSplit}%`,
          minWidth: 320,
          // Fixed `height`, not `minHeight` — matches the left column's own convention (Preview's
          // own content — one bounded image — never needs to grow past it the way
          // RegExpTesterPanel.tsx's/TextDiffPanel.tsx's own free-form results can, so there's no
          // floor-vs-fixed distinction to make here).
          height: availableHeight,
          display: previewHidden ? 'none' : 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        <PanelHeader title="Preview" actionsSpacing={0.5}>
          <Tooltip title={copiedKey === 'preview' ? 'Copied!' : 'Copy Data URL'}>
            <span>
              <IconButton size="small" onClick={handleCopyPreview} disabled={!input}>
                {copiedKey === 'preview' ? (
                  <CheckIcon fontSize="small" color="success" />
                ) : (
                  <ContentCopyIcon fontSize="small" />
                )}
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title="Download image">
            <span>
              <IconButton size="small" onClick={handleDownload} disabled={!canDownload}>
                <DownloadIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title={maximizedPanel === 'preview' ? 'Restore split view' : 'Maximize Preview'}>
            <IconButton size="small" onClick={toggleMaximizePreview}>
              {maximizedPanel === 'preview' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </PanelHeader>
        <Box
          sx={{
            flex: 1,
            minHeight: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            p: 2,
            overflow: 'auto',
            // Checkerboard only while an actual image is showing (it exists to reveal that
            // image's own transparency) — plain white, per request, whenever there's nothing to
            // preview or the Data URL failed to load, rather than the pattern implying content
            // that isn't there.
            ...(input && !previewFailed ? CHECKERBOARD_BACKGROUND : { bgcolor: CHECKERBOARD_LIGHT }),
          }}
        >
          {!input ? (
            <Typography variant="body2" color="text.secondary">
              Image preview will appear here.
            </Typography>
          ) : previewFailed ? (
            <Stack spacing={1} alignItems="center">
              <BrokenImageIcon sx={{ fontSize: 40, color: 'grey.400' }} />
              <Typography variant="body2" color="text.secondary">
                Could not load this image — check the Data URL is valid.
              </Typography>
            </Stack>
          ) : (
            <img
              src={input}
              alt="Preview"
              style={{ maxWidth: '100%', maxHeight: 400, objectFit: 'contain' }}
              onError={() => setPreviewFailed(true)}
              onLoad={() => setPreviewFailed(false)}
            />
          )}
        </Box>
      </Paper>
    </Box>
  );
}
