import { useCallback, useRef, useState, DragEvent as ReactDragEvent, ChangeEvent, ClipboardEvent as ReactClipboardEvent } from 'react';
import { Box, Button, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUploadOutlined';
import ContentPasteIcon from '@mui/icons-material/ContentPasteOutlined';
import BrokenImageIcon from '@mui/icons-material/BrokenImageOutlined';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import DownloadIcon from '@mui/icons-material/DownloadOutlined';
import CheckIcon from '@mui/icons-material/CheckOutlined';
import { useNotification } from '@shared/contexts/NotificationContext';
import PanelHeader from './PanelHeader';
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
   * shared `DevUtilToolPanel`'s Input card and the sidebar both size themselves to (see that
   * component's own doc comment for how). Applied only to the Preview card, per request, matching
   * Input's own "fixed `height`, not `minHeight`" treatment (Preview's own content — one bounded
   * image — never needs to grow past it the way Output's free-form text can, so there's no floor-
   * vs-fixed distinction to make here). The Upload/Image Data URL column keeps its own natural,
   * content-driven height, unaffected by this. */
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

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
      <Box sx={{ flex: '1 1 45%', minWidth: 320, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Paper variant="outlined">
          <PanelHeader title="Upload Image" />
          <Box sx={{ p: 2 }}>
            <input
              ref={fileInputRef}
              type="file"
              accept={FILE_INPUT_ACCEPT}
              style={{ display: 'none' }}
              onChange={handleFileInputChange}
            />
            {/* No border at rest — per request, this used to draw its own dashed box nested
                inside this card's own Paper border, reading as "a box inside a box." A bgcolor
                shift on hover/drag (plus the cursor and the icon/text themselves) is enough
                affordance that this is clickable/droppable without a persistent border. */}
            <Box
              onClick={() => fileInputRef.current?.click()}
              onDragOver={e => {
                e.preventDefault();
                setDragActive(true);
              }}
              onDragLeave={() => setDragActive(false)}
              onDrop={handleDrop}
              sx={{
                borderRadius: 1,
                p: 4,
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
          </Box>
        </Paper>

        <Paper variant="outlined">
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
          </PanelHeader>
          <Box sx={{ p: 2 }}>
            <TextField
              value={input}
              onChange={e => {
                setPreviewFailed(false);
                onInputChange(e.target.value);
              }}
              onPaste={handlePaste}
              placeholder={inputPlaceholder}
              multiline
              minRows={6}
              maxRows={10}
              fullWidth
              sx={{
                '& .MuiInputBase-input': { fontFamily: 'monospace', fontSize: '0.75rem', wordBreak: 'break-all' },
                ...HIDDEN_TEXT_FIELD_OUTLINE_SX,
              }}
            />
          </Box>
        </Paper>
      </Box>

      <Paper
        variant="outlined"
        sx={{
          flex: '1 1 45%',
          minWidth: 320,
          // Fixed `height`, not `minHeight` — matches the Input card's own convention (Output's
          // own "floor only, grows for long content" shape doesn't apply here: Preview's content
          // is one bounded image, never open-ended text). Per request, this matches the same
          // viewport-relative height the shared DevUtilToolPanel's Input card and the sidebar
          // both already size themselves to.
          height: availableHeight,
          display: 'flex',
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
