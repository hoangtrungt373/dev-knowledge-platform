import { useCallback, useState } from 'react';
import { Box, Button, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import ContentPasteIcon from '@mui/icons-material/ContentPasteOutlined';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import CheckIcon from '@mui/icons-material/CheckOutlined';
import PlayArrowIcon from '@mui/icons-material/PlayArrowOutlined';
import SubmitButton from '@shared/components/SubmitButton';
import { useNotification } from '@shared/contexts/NotificationContext';
import { DevUtilsResponse } from '../types';
import PanelHeader from './PanelHeader';
import { useCopyFeedback } from '../hooks/useCopyFeedback';
import { HIDDEN_TEXT_FIELD_OUTLINE_SX } from '../utils/textFieldStyles';

interface HashGeneratorPanelProps {
  /** Controlled — same lifted `input`/`output` state `DevUtilsPage.tsx` already threads into
   * `DevUtilToolPanel` for every other operation, so this operation's headline row (Sample/Clear)
   * keeps working unchanged even though the rest of this panel's own layout doesn't. */
  input: string;
  onInputChange: (value: string) => void;
  output: string | null;
  onOutputChange: (value: string | null) => void;
  actionLabel: string;
  inputPlaceholder: string;
  /** Same `Promise<DevUtilsResponse>` contract every operation's `onSubmit` returns — `output` is
   * `config/operations.tsx#formatHashResult`'s own "`<Label>\n<value>` pairs, blank-line separated"
   * text, parsed back apart by `parseHashLines` below rather than threading a second, richer
   * response type down from `DevUtilsPage.tsx`. */
  onSubmit: (input: string, minify: boolean) => Promise<DevUtilsResponse>;
}

// One color per algorithm, so the four cards read as distinct at a glance rather than four
// identical grey headlines — the same "badge per type" convention
// config/outputLanguages.ts#OUTPUT_LANGUAGE_INFO already establishes for the shared panel, just
// with darker (700/800-weight) hues chosen for contrast against this card's own fixed white
// background instead of that map's dark-background-tuned ones. Keyed by the exact label string
// `config/operations.tsx#formatHashResult` emits; `grey.900` is the fallback for anything
// unmapped (there isn't one today, but a typo'd/future label should still render legibly rather
// than disappearing).
const HASH_LABEL_COLORS: Record<string, string> = {
  'SHA-1': '#1565c0',
  'SHA-256': '#2e7d32',
  'SHA-384': '#6a1b9a',
  'SHA-512': '#c62828',
};

// Reverses config/operations.tsx#formatHashResult's own formatting convention. Local to this file
// (not shared with DevUtilToolPanel.tsx, which never renders this operation at all) — Hash
// Generator is the only operation using this layout today.
function parseHashLines(output: string): Array<{ label: string; value: string }> {
  return output
    .split('\n\n')
    .filter(chunk => chunk.length > 0)
    .map(chunk => {
      const newlineIndex = chunk.indexOf('\n');
      return newlineIndex === -1
        ? { label: chunk, value: '' }
        : { label: chunk.slice(0, newlineIndex), value: chunk.slice(newlineIndex + 1) };
    });
}

/**
 * Hash Generator's own bespoke Input/Output layout — a dedicated component, not a mode grafted
 * onto the shared `DevUtilToolPanel.tsx`, per direct request: this operation's result (four
 * independent digests) doesn't fit that component's single-code-editor Output design, and this
 * page needs a real precedent for "some operations use a genuinely different layout" rather than
 * one shared component trying to grow a special case for every future one. `DevUtilsPage.tsx`
 * renders this in place of `DevUtilToolPanel` for exactly this one operation; every other
 * operation still goes through the shared component unchanged.
 *
 * <p>Deliberately much simpler than the shared panel — no CodeMirror, no resizable split, no
 * maximize, no minify toggle: this operation's input is plain text with nothing to syntax-
 * highlight, and its result is four short, independent strings, never a large block of code.
 *
 * <p>Each digest renders as its own **white** (`#ffffff`, a fixed literal — always white
 * regardless of the app's light/dark theme, per request) bordered card: a headline (the algorithm
 * name) and a monospace body (the digest itself), each with its own Copy button and independent
 * "Copied!" feedback — copying one digest out of a single concatenated block was the actual
 * problem this layout exists to fix.
 */
export default function HashGeneratorPanel({
  input,
  onInputChange,
  output,
  onOutputChange,
  actionLabel,
  inputPlaceholder,
  onSubmit,
}: HashGeneratorPanelProps): JSX.Element {
  const { showError } = useNotification();
  const [saving, setSaving] = useState(false);
  // `useCopyFeedback`'s own `key` param is what gives each card its own independent "Copied!"
  // feedback — keyed by label here, so copying one digest never shows "Copied!" on another card.
  const { copiedKey, copy } = useCopyFeedback();

  const handleGenerate = useCallback(async () => {
    setSaving(true);
    try {
      const result = await onSubmit(input, false);
      onOutputChange(result.output);
    } catch (submitError) {
      onOutputChange(null);
      const message = submitError instanceof Error ? submitError.message : 'Could not generate hashes.';
      showError(message);
    } finally {
      setSaving(false);
    }
  }, [input, onSubmit, onOutputChange, showError]);

  const handlePaste = useCallback(async () => {
    try {
      const text = await navigator.clipboard.readText();
      onInputChange(text);
    } catch {
      showError('Could not read from the clipboard — check your browser permissions.');
    }
  }, [onInputChange, showError]);

  const handleCopyValue = useCallback((label: string, value: string) => copy(value, label), [copy]);

  const hashes = output !== null ? parseHashLines(output) : [];

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
      <Paper variant="outlined" sx={{ flex: '1 1 45%', minWidth: 320 }}>
        <PanelHeader title="Input">
          <Button size="small" variant="outlined" startIcon={<ContentPasteIcon fontSize="small" />} onClick={handlePaste}>
            Paste
          </Button>
          <SubmitButton
            size="small"
            saving={saving}
            label={actionLabel}
            startIcon={<PlayArrowIcon fontSize="small" />}
            onClick={handleGenerate}
            disabled={!input.trim()}
          />
        </PanelHeader>
        <Box sx={{ p: 2 }}>
          <TextField
            value={input}
            onChange={e => onInputChange(e.target.value)}
            placeholder={inputPlaceholder}
            multiline
            minRows={10}
            maxRows={20}
            fullWidth
            sx={{
              '& .MuiInputBase-input': { fontFamily: 'monospace', fontSize: '0.85rem' },
              ...HIDDEN_TEXT_FIELD_OUTLINE_SX,
            }}
          />
        </Box>
      </Paper>

      <Box sx={{ flex: '1 1 45%', minWidth: 320, display: 'flex', flexDirection: 'column', gap: 2 }}>
        {hashes.length === 0 ? (
          <Paper
            variant="outlined"
            sx={{ p: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'background.paper' }}
          >
            <Typography variant="body2" color="text.secondary">
              Generated hashes will appear here.
            </Typography>
          </Paper>
        ) : (
          hashes.map(({ label, value }) => (
            <Paper key={label} variant="outlined" sx={{ p: 2, bgcolor: '#ffffff' }}>
              <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
                {/* Per-algorithm color (HASH_LABEL_COLORS), not the semantic `text.primary`
                    token — this card's own background is a fixed `#ffffff` regardless of the
                    app's light/dark mode, but `text.primary` itself flips to a light color in
                    dark mode, which would be nearly invisible against a background that never
                    follows it. Fixed hex literals stay legible (and distinct per algorithm)
                    against this card's own always-white background either way. */}
                <Typography variant="subtitle2" fontWeight={700} sx={{ color: HASH_LABEL_COLORS[label] ?? 'grey.900' }}>
                  {label}
                </Typography>
                <Tooltip title={copiedKey === label ? 'Copied!' : 'Copy'}>
                  <IconButton size="small" onClick={() => handleCopyValue(label, value)}>
                    {copiedKey === label ? (
                      <CheckIcon fontSize="small" color="success" />
                    ) : (
                      <ContentCopyIcon fontSize="small" />
                    )}
                  </IconButton>
                </Tooltip>
              </Stack>
              <Typography
                component="pre"
                sx={{
                  m: 0,
                  color: 'grey.800',
                  fontFamily: 'monospace',
                  fontSize: '0.8rem',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                }}
              >
                {value}
              </Typography>
            </Paper>
          ))
        )}
      </Box>
    </Box>
  );
}
