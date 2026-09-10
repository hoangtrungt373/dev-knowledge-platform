import { useCallback, useState } from 'react';
import { Box, Button, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import ContentPasteIcon from '@mui/icons-material/ContentPasteOutlined';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import DownloadIcon from '@mui/icons-material/DownloadOutlined';
import PlayArrowIcon from '@mui/icons-material/PlayArrowOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import SubmitButton from '@shared/components/SubmitButton';
import { useNotification } from '@shared/contexts/NotificationContext';
import { DevUtilsResponse } from '../types';
import { DevUtilError } from '../utils/errorFormatting';
import { downloadTextFile } from '../utils/downloadTextFile';
import { parseRegexInput, serializeRegexInput } from '../utils/regexInputFormat';
import PanelHeader from './PanelHeader';
import { useCopyFeedback } from '../hooks/useCopyFeedback';
import { HIDDEN_TEXT_FIELD_OUTLINE_SX } from '../utils/textFieldStyles';

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
 * {@code /pattern/flags} literal) and **Test String** (a plain multiline box, with Paste and the
 * actual Test action in its own header). Both write into the same lifted `input` string via
 * `utils/regexInputFormat.ts`'s serialize/parse pair, so Sample/Clear (and a bookmarked/shared
 * hash link) keep working exactly like every other operation's — see that file's own doc comment
 * for the exact format and why it's safe to round-trip.
 *
 * <p>The **Output** card is a plain read-only text block (no CodeMirror — matches are a short
 * list of extracted strings, not code to syntax-highlight, the same "much simpler than the shared
 * panel" choice `HashGeneratorPanel.tsx` already makes), blank-line separated exactly as the
 * backend returns them (a match can itself contain a newline, so no separator at all would make
 * two matches indistinguishable from one). Copy/Download act on the whole block, mirroring the
 * shared panel's own Output header actions.
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
}: RegExpTesterPanelProps): JSX.Element {
  const { showError } = useNotification();
  const [saving, setSaving] = useState(false);
  const { copiedKey, copy } = useCopyFeedback();

  const { pattern, flags, testText } = parseRegexInput(input);

  const updateField = useCallback(
    (field: 'pattern' | 'flags' | 'testText', value: string) => {
      onInputChange(serializeRegexInput({ pattern, flags, testText, [field]: value }));
    },
    [onInputChange, pattern, flags, testText]
  );

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

  const handlePasteTestText = useCallback(async () => {
    try {
      const text = await navigator.clipboard.readText();
      updateField('testText', text);
    } catch {
      showError('Could not read from the clipboard — check your browser permissions.');
    }
  }, [updateField, showError]);

  const handleCopy = useCallback(() => {
    if (output === null) return;
    copy(output, 'output');
  }, [output, copy]);

  const handleDownload = useCallback(() => {
    if (output === null) return;
    downloadTextFile(downloadFileName, output);
  }, [output, downloadFileName]);

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
      <Box sx={{ flex: '1 1 45%', minWidth: 320, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Paper variant="outlined">
          <PanelHeader title="Pattern" />
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

        <Paper variant="outlined">
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
          </PanelHeader>
          <Box sx={{ p: 2 }}>
            <TextField
              value={testText}
              onChange={e => updateField('testText', e.target.value)}
              placeholder={'hello@vuicoding.me\nsupport@example.com\nnot-an-email'}
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
      </Box>

      <Paper variant="outlined" sx={{ flex: '1 1 45%', minWidth: 320, display: 'flex', flexDirection: 'column' }}>
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
        </PanelHeader>
        <Box sx={{ p: 2, flex: 1, minHeight: 240, maxHeight: 600, overflow: 'auto' }}>
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
                <Typography
                  component="pre"
                  variant="body2"
                  color="text.primary"
                  sx={{ m: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontFamily: 'monospace' }}
                >
                  {error.detail}
                </Typography>
              </Box>
            </Stack>
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
