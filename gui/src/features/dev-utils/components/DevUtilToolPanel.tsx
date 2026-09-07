import { useCallback, useState } from 'react';
import { Box, Checkbox, FormControlLabel, Paper, Stack, TextField, Typography } from '@mui/material';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import SubmitButton from '@shared/components/SubmitButton';
import CopyIconButton from '@shared/components/CopyIconButton';
import { DevUtilsResponse } from '../types';

interface DevUtilToolPanelProps {
  actionLabel: string;
  inputLabel: string;
  inputPlaceholder: string;
  /** Prism language for the output syntax highlighter: 'json' | 'yaml' | 'markup' (HTML). */
  outputLanguage: string;
  /** Whether this tool exposes a minify checkbox at all — false only for JSON→YAML, which has no
   * minify concept (see devUtilsApi.jsonToYaml's own comment). */
  supportsMinify: boolean;
  onSubmit: (input: string, minify: boolean) => Promise<DevUtilsResponse>;
}

/** The one reusable panel every /dev-utils tab renders — input, an optional minify toggle, a
 * submit button, and a syntax-highlighted read-only output with a copy button. Each tab configures
 * it for its own operation rather than this component knowing about any specific one. */
export default function DevUtilToolPanel({
  actionLabel,
  inputLabel,
  inputPlaceholder,
  outputLanguage,
  supportsMinify,
  onSubmit,
}: DevUtilToolPanelProps): JSX.Element {
  const [input, setInput] = useState('');
  const [minify, setMinify] = useState(false);
  const [output, setOutput] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const handleSubmit = useCallback(async () => {
    setSaving(true);
    try {
      const result = await onSubmit(input, minify);
      setOutput(result.output);
    } catch {
      // showError already called by httpClient
    } finally {
      setSaving(false);
    }
  }, [input, minify, onSubmit]);

  return (
    <Stack spacing={2}>
      <TextField
        label={inputLabel}
        placeholder={inputPlaceholder}
        multiline
        rows={10}
        fullWidth
        value={input}
        onChange={e => setInput(e.target.value)}
      />

      <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between">
        {supportsMinify ? (
          <FormControlLabel
            control={<Checkbox checked={minify} onChange={e => setMinify(e.target.checked)} />}
            label="Minify output"
          />
        ) : (
          <Box />
        )}
        <SubmitButton saving={saving} label={actionLabel} onClick={handleSubmit} disabled={!input.trim()} />
      </Stack>

      {output !== null && (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          <Stack
            direction="row"
            alignItems="center"
            justifyContent="space-between"
            sx={{ px: 2, py: 1, borderBottom: 1, borderColor: 'divider' }}
          >
            <Typography variant="subtitle2">Output</Typography>
            <CopyIconButton value={output} />
          </Stack>
          <SyntaxHighlighter
            language={outputLanguage}
            style={vscDarkPlus}
            customStyle={{
              margin: 0,
              borderRadius: 0,
              fontSize: '0.8rem',
              padding: '12px 16px',
              maxHeight: 400,
              overflow: 'auto',
            }}
          >
            {output}
          </SyntaxHighlighter>
        </Paper>
      )}
    </Stack>
  );
}
