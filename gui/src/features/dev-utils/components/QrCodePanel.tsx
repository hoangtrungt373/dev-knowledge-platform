import { useCallback, useEffect, useRef, useState, ChangeEvent, DragEvent as ReactDragEvent, ClipboardEvent as ReactClipboardEvent } from 'react';
import { Box, Button, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import CloudUploadIcon from '@mui/icons-material/CloudUploadOutlined';
import ContentPasteIcon from '@mui/icons-material/ContentPasteOutlined';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import CheckIcon from '@mui/icons-material/CheckOutlined';
import DownloadIcon from '@mui/icons-material/DownloadOutlined';
import OpenInNewIcon from '@mui/icons-material/OpenInNewOutlined';
import QrCode2Icon from '@mui/icons-material/QrCode2Outlined';
import OpenInFullIcon from '@mui/icons-material/OpenInFullOutlined';
import CloseFullscreenIcon from '@mui/icons-material/CloseFullscreenOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import QRCode from 'qrcode';
import jsQR from 'jsqr';
import SubmitButton from '@shared/components/SubmitButton';
import { useNotification } from '@shared/contexts/NotificationContext';
import { DevUtilError } from '../utils/errorFormatting';
import { downloadTextFile } from '../utils/downloadTextFile';
import { useResizableSplit } from '../hooks/useResizableSplit';
import { usePanelMaximize } from '../hooks/usePanelMaximize';
import PanelHeader from './PanelHeader';
import PanelResizeHandle from './PanelResizeHandle';
import { useCopyFeedback } from '../hooks/useCopyFeedback';
import { HIDDEN_TEXT_FIELD_OUTLINE_SX } from '../utils/textFieldStyles';

interface QrCodePanelProps {
  /** Controlled — same lifted `input` state `DevUtilsPage.tsx` already threads into
   * `DevUtilToolPanel` for every other operation. Doubles as the *one* shared text value for both
   * directions: the Generate card reads it to build a QR code, and a successful Read overwrites it
   * with whatever text the scanned code decoded to — see this component's own doc comment for why
   * that's a deliberate design choice, not an accident. */
  input: string;
  onInputChange: (value: string) => void;
  inputPlaceholder: string;
  actionLabel: string;
  availableHeight: number;
}

// A generous but finite cap, purely a client-side UX safeguard — the same role
// Base64ImagePanel.tsx's own MAX_FILE_SIZE_BYTES plays, reused verbatim (there is no backend round
// trip here either to enforce one).
const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

const ALLOWED_IMAGE_TYPES: Record<string, string> = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/webp': 'webp',
  'image/gif': 'gif',
};
const ALLOWED_IMAGE_TYPES_LABEL = 'PNG, JPG, WebP, GIF';
const FILE_INPUT_ACCEPT = Object.keys(ALLOWED_IMAGE_TYPES).join(',');

const URL_PATTERN = /^https?:\/\//i;

function formatBytes(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Decodes whatever QR code is in `imageDataUrl`, via an off-screen `<canvas>` (the only way to get
 * at an image's raw pixels in a browser) and `jsQR` — rejects with a plain `Error` (never a special
 * type) for every failure mode (a corrupt image, a genuine image with no QR code in it, a browser
 * that somehow can't hand back a 2D canvas context), so the caller's own single `catch` already
 * covers all of them. */
function decodeQrFromDataUrl(imageDataUrl: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = image.naturalWidth;
      canvas.height = image.naturalHeight;
      const context = canvas.getContext('2d');
      if (!context) {
        reject(new Error('This browser could not read the image data.'));
        return;
      }
      context.drawImage(image, 0, 0);
      const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
      const decoded = jsQR(pixels.data, pixels.width, pixels.height);
      if (!decoded) {
        reject(new Error('No QR code found in this image.'));
        return;
      }
      resolve(decoded.data);
    };
    image.onerror = () => reject(new Error('Could not load this image.'));
    image.src = imageDataUrl;
  });
}

/**
 * QR Code Reader/Generator's own bespoke Input/Output layout — a dedicated component, not a mode
 * grafted onto the shared `DevUtilToolPanel.tsx`, per direct request: this operation has two
 * genuinely different input shapes (typed text for Generate, an uploaded image for Read) and two
 * genuinely different output shapes (a rendered QR image, or decoded text), none of which fit that
 * component's plain-text Input/Output editor pair. **Structurally a direct extension of
 * `Base64ImagePanel.tsx`'s own template**, per direct request — the same 3-card shape (two stacked
 * cards on the left, one Output card on the right, a vertical split between the stacked pair plus
 * a horizontal split against Output, a 3-way maximize), and the Read card's own upload/drag-drop/
 * clipboard-paste mechanics are a close adaptation of that component's own `handleFile`/
 * `handleDrop`/`handlePasteButtonClick` trio.
 *
 * <p>**Entirely client-side — no `dev-utils-service` endpoint, no `onSubmit` call at all**, the
 * same genuine exception to "every operation calls a backend endpoint"
 * `Base64ImagePanel.tsx`/`config/operations.tsx#base64-image` already establish: generating a QR
 * code (`qrcode`, MIT-licensed) and decoding one (`jsqr`, MIT-licensed, operating on a `<canvas>`'s
 * own pixel data) are both small, well-established, purely computational libraries — round-
 * tripping either through the backend would only add latency for zero benefit.
 *
 * <p>**`input` is deliberately shared by both directions, not two independent pieces of state** —
 * the Generate card's own text field reads it, and a successful Read *writes* the decoded text
 * back into it (not just into a separate "result" field). Two reasons: (1) it lets a scanned code's
 * own content be immediately re-generated/edited/re-scanned without retyping it, a genuinely useful
 * round-trip for the "read a link, then do something with it" workflow this operation exists for;
 * (2) it's what makes `DevUtilsPage.tsx`'s own Clear button actually clear *both* a generated image
 * and a decoded-text result — that page's Clear only ever resets the lifted `input` back to `''`,
 * so a `useEffect` watching for that transition (the same fix `ColorConverterPanel.tsx`/
 * `TextDiffPanel.tsx` already needed for their own local result state) only reliably clears
 * *every* path through this panel if every path that produces a result also passes through
 * `input` at some point — which sharing it for both directions guarantees, and two independent
 * fields would not have (a Read-only session would otherwise leave `input` at `''` the whole time,
 * so Clear would have nothing to transition and the decoded result would silently survive it).
 *
 * <p>**Generate** is an explicit action (`SubmitButton`, since `QRCode.toDataURL` is async) —
 * builds a `data:image/png;base64,...` QR code from `input` at a fixed 256px/margin-2 size (no
 * configurable size/error-correction-level UI, a deliberate scope trim — this operation's own
 * defaults already produce a normal, scannable code for the common case). **Read** is *not* a
 * separate button click — decoding a canvas's own already-loaded pixels is effectively
 * instantaneous, so it fires automatically the instant an image is provided (upload, drag-drop, or
 * paste), the same "no confirm step needed" reasoning a real barcode-scanner UI would follow.
 *
 * <p>A failed Read (no QR code in the image) or a failed Generate (e.g. text too long for a QR
 * code's own capacity limits) renders inline in the Output panel — the same treatment
 * `RegExpTesterPanel.tsx`/`ColorConverterPanel.tsx` already established, since either failure is a
 * plausible, expected outcome of normal use, not a rare edge case a toast's own disappearing act
 * would be an acceptable loss for. A decoded result that looks like a URL (`http(s)://...`) gets an
 * extra "Open" button, since this operation's own second half is literally framed as "generate a
 * link from a QR code."
 *
 * <p>Same resizable-split-plus-3-way-maximize mechanism `Base64ImagePanel.tsx` itself uses — see
 * that component's own doc comment for the full mechanics (this one reuses the identical
 * `hooks/useResizableSplit.ts`/`hooks/usePanelMaximize.ts`/`components/PanelResizeHandle.tsx`
 * extraction, including the same 30/70 default vertical split, since the Generate card's own
 * single text field needs even less room than the Read card's drop-zone).
 */
export default function QrCodePanel({
  input,
  onInputChange,
  inputPlaceholder,
  actionLabel,
  availableHeight,
}: QrCodePanelProps): JSX.Element {
  const { showError } = useNotification();
  const [generating, setGenerating] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const [mode, setMode] = useState<'generate' | 'read' | null>(null);
  const [qrImageDataUrl, setQrImageDataUrl] = useState<string | null>(null);
  const [error, setError] = useState<DevUtilError | null>(null);
  const { copiedKey, copy } = useCopyFeedback();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { maximizedPanel, toggle: toggleMaximize } = usePanelMaximize<'generate' | 'read' | 'output'>();
  const toggleMaximizeGenerate = useCallback(() => toggleMaximize('generate'), [toggleMaximize]);
  const toggleMaximizeRead = useCallback(() => toggleMaximize('read'), [toggleMaximize]);
  const toggleMaximizeOutput = useCallback(() => toggleMaximize('output'), [toggleMaximize]);

  // Horizontal: the left column (Generate + Read together) vs. Output — the same shape
  // Base64ImagePanel.tsx already uses for its own Upload+Data-URL-column-vs-Preview split.
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
  } = useResizableSplit({ storageKey: 'devUtilsQrCodePanelHorizontalSplitPercent' });

  // Vertical: Generate vs. Read, stacked inside the left column — defaults 30/70, the same
  // "smaller side starts smaller" reasoning Base64ImagePanel.tsx's own Upload/Image Data URL split
  // already establishes (a single text field needs even less room than a drop-zone).
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
    storageKey: 'devUtilsQrCodePanelVerticalSplitPercent',
    orientation: 'vertical',
    defaultPercent: 30,
  });

  // Mirrors DevUtilsPage.tsx's own Clear behavior for every other panel with local result state —
  // see this component's own doc comment for why sharing `input` across both directions is what
  // makes this actually reliable here, unlike two independent fields would have been.
  useEffect(() => {
    if (input === '') {
      setQrImageDataUrl(null);
      setMode(null);
      setError(null);
    }
  }, [input]);

  const handleGenerate = useCallback(async () => {
    setGenerating(true);
    try {
      const dataUrl = await QRCode.toDataURL(input, { width: 256, margin: 2 });
      setError(null);
      setQrImageDataUrl(dataUrl);
      setMode('generate');
    } catch (submitError) {
      setQrImageDataUrl(null);
      setMode(null);
      const message = submitError instanceof Error ? submitError.message : 'Could not generate a QR code from this text.';
      setError({ headline: 'Cannot be processed', detail: message });
    } finally {
      setGenerating(false);
    }
  }, [input]);

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
      reader.onload = async () => {
        try {
          const decoded = await decodeQrFromDataUrl(String(reader.result));
          setError(null);
          setMode('read');
          onInputChange(decoded);
        } catch (readError) {
          setMode(null);
          const message = readError instanceof Error ? readError.message : 'Could not read a QR code from this image.';
          setError({ headline: 'Cannot be processed', detail: message });
        }
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

  // Lets Ctrl+V paste an image directly into the drop-zone once it's focused (tabIndex below), the
  // same native-paste affordance Base64ImagePanel.tsx's own Image Data URL box establishes — no
  // text fallback here, since this card has no text field of its own to paste plain text into.
  const handleDropzonePaste = useCallback(
    (e: ReactClipboardEvent<HTMLDivElement>) => {
      const items = e.clipboardData?.items;
      if (!items) return;
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
      showError('The clipboard has no image to read a QR code from.');
    } catch {
      showError('Could not read from the clipboard — check your browser permissions.');
    }
  }, [handleFile, showError]);

  const handleCopyImage = useCallback(() => {
    if (!qrImageDataUrl) return;
    copy(qrImageDataUrl, 'image');
  }, [qrImageDataUrl, copy]);

  const handleDownloadImage = useCallback(() => {
    if (!qrImageDataUrl) return;
    const link = document.createElement('a');
    link.href = qrImageDataUrl;
    link.download = 'qr-code.png';
    link.click();
  }, [qrImageDataUrl]);

  const handleCopyText = useCallback(() => {
    if (!input) return;
    copy(input, 'text');
  }, [input, copy]);

  const handleDownloadText = useCallback(() => {
    if (!input) return;
    downloadTextFile('qr-code-content.txt', input);
  }, [input]);

  const isDecodedUrl = mode === 'read' && URL_PATTERN.test(input.trim());

  const generateHidden = maximizedPanel === 'read' || maximizedPanel === 'output';
  const readHidden = maximizedPanel === 'generate' || maximizedPanel === 'output';
  const leftColumnHidden = maximizedPanel === 'output';
  const outputHidden = maximizedPanel === 'generate' || maximizedPanel === 'read';

  return (
    // `position: 'relative'` anchors the horizontal PanelResizeHandle; no `gap` — the left column
    // and Output's own flex-basis percentages sum to 100%, mirroring every other split row in this
    // feature.
    <Box ref={rowRef} sx={{ position: 'relative', display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start' }}>
      <Box
        ref={columnRef}
        sx={{
          position: 'relative',
          flex: maximizedPanel === 'generate' || maximizedPanel === 'read' ? '1 1 100%' : `1 1 ${horizontalSplit}%`,
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
            flex: maximizedPanel === 'generate' ? '1 1 100%' : `1 1 ${verticalSplit}%`,
            minHeight: 0,
            display: generateHidden ? 'none' : 'flex',
            flexDirection: 'column',
            overflow: 'auto',
          }}
        >
          <PanelHeader title="Generate QR Code">
            <SubmitButton
              size="small"
              saving={generating}
              label={actionLabel}
              startIcon={<QrCode2Icon fontSize="small" />}
              onClick={handleGenerate}
              disabled={!input.trim()}
            />
            <Tooltip title={maximizedPanel === 'generate' ? 'Restore split view' : 'Maximize Generate QR Code'}>
              <IconButton size="small" onClick={toggleMaximizeGenerate}>
                {maximizedPanel === 'generate' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </PanelHeader>
          <Box sx={{ p: 2 }}>
            <TextField
              value={input}
              onChange={e => onInputChange(e.target.value)}
              placeholder={inputPlaceholder}
              fullWidth
              size="small"
              sx={{ '& .MuiInputBase-input': { fontFamily: 'monospace', fontSize: '0.85rem' }, ...HIDDEN_TEXT_FIELD_OUTLINE_SX }}
            />
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
              Any text or URL
            </Typography>
          </Box>
        </Paper>

        <PanelResizeHandle
          ariaLabel="Resize Generate/Read panels"
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
            flex: maximizedPanel === 'read' ? '1 1 100%' : `1 1 ${100 - verticalSplit}%`,
            minHeight: 0,
            display: readHidden ? 'none' : 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          <PanelHeader title="Read QR Code">
            <Button
              size="small"
              variant="outlined"
              startIcon={<ContentPasteIcon fontSize="small" />}
              onClick={handlePasteButtonClick}
            >
              Paste
            </Button>
            <Tooltip title={maximizedPanel === 'read' ? 'Restore split view' : 'Maximize Read QR Code'}>
              <IconButton size="small" onClick={toggleMaximizeRead}>
                {maximizedPanel === 'read' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
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
          {/* No border at rest, matching Base64ImagePanel.tsx's own drop-zone convention — a
              bgcolor shift on hover/drag (plus the cursor/icon/text) is enough affordance without
              one. tabIndex + onPaste lets a click-then-Ctrl+V also work, alongside the explicit
              Paste button above and drag-and-drop below. */}
          <Box
            tabIndex={0}
            onClick={() => fileInputRef.current?.click()}
            onPaste={handleDropzonePaste}
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
              '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: -2 },
              transition: 'background-color 0.1s',
            }}
          >
            <CloudUploadIcon sx={{ fontSize: 40, color: 'grey.500' }} />
            <Typography variant="body2" fontWeight={600} sx={{ mt: 1 }}>
              Drop, paste, or select a QR code image
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {ALLOWED_IMAGE_TYPES_LABEL} (up to {formatBytes(MAX_FILE_SIZE_BYTES)})
            </Typography>
          </Box>
        </Paper>
      </Box>

      <PanelResizeHandle
        ariaLabel="Resize Generate/Read column and Output panels"
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
          height: availableHeight,
          display: outputHidden ? 'none' : 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        <PanelHeader title="Output" actionsSpacing={0.5}>
          {mode === 'generate' && qrImageDataUrl !== null && (
            <>
              <Tooltip title={copiedKey === 'image' ? 'Copied!' : 'Copy Data URL'}>
                <IconButton size="small" onClick={handleCopyImage}>
                  {copiedKey === 'image' ? <CheckIcon fontSize="small" color="success" /> : <ContentCopyIcon fontSize="small" />}
                </IconButton>
              </Tooltip>
              <Tooltip title="Download PNG">
                <IconButton size="small" onClick={handleDownloadImage}>
                  <DownloadIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </>
          )}
          {mode === 'read' && (
            <>
              {isDecodedUrl && (
                <Tooltip title="Open link">
                  <IconButton size="small" component="a" href={input} target="_blank" rel="noreferrer noopener">
                    <OpenInNewIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
              <Tooltip title={copiedKey === 'text' ? 'Copied!' : 'Copy'}>
                <IconButton size="small" onClick={handleCopyText}>
                  {copiedKey === 'text' ? <CheckIcon fontSize="small" color="success" /> : <ContentCopyIcon fontSize="small" />}
                </IconButton>
              </Tooltip>
              <Tooltip title="Download as .txt">
                <IconButton size="small" onClick={handleDownloadText}>
                  <DownloadIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </>
          )}
          <Tooltip title={maximizedPanel === 'output' ? 'Restore split view' : 'Maximize Output'}>
            <IconButton size="small" onClick={toggleMaximizeOutput}>
              {maximizedPanel === 'output' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </PanelHeader>
        <Box sx={{ flex: 1, minHeight: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', p: 2, overflow: 'auto' }}>
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
          ) : mode === 'generate' && qrImageDataUrl !== null ? (
            <img src={qrImageDataUrl} alt="Generated QR code" style={{ maxWidth: '100%', maxHeight: '100%' }} />
          ) : mode === 'read' ? (
            <Box sx={{ width: '100%' }}>
              <Typography
                component="pre"
                sx={{ m: 0, fontFamily: 'monospace', fontSize: '0.9rem', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}
              >
                {input}
              </Typography>
            </Box>
          ) : (
            <Typography variant="body2" color="text.secondary">
              Generate or read a QR code to see the result here.
            </Typography>
          )}
        </Box>
      </Paper>
    </Box>
  );
}
