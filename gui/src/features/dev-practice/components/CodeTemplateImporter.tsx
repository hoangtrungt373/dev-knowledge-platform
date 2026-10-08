import { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Paper,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
  useTheme,
} from '@mui/material';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import CodeMirror from '@uiw/react-codemirror';
import { EditorView } from '@codemirror/view';
import { ParsedSignature, ProgrammingLanguage } from '../types';
import { devPracticeApi } from '../api/devPracticeApi';
import SubmitButton from '@shared/components/SubmitButton';
import { LANGUAGES } from '../constants';
import { LANGUAGE_EXTENSIONS } from '../utils/codeLanguages';

// What each language's template has to look like — Python needs type hints and JavaScript needs
// JSDoc, since neither spells types in the signature itself.
const PLACEHOLDERS: Record<ProgrammingLanguage, string> = {
  JAVA: 'class Solution {\n    public int evalRPN(String[] tokens) {\n\n    }\n}',
  PYTHON: 'class Solution:\n    def evalRPN(self, tokens: List[str]) -> int:\n        pass',
  JAVASCRIPT: '/**\n * @param {string[]} tokens\n * @return {number}\n */\nvar evalRPN = function(tokens) {\n\n};',
};

const chrome = EditorView.theme({
  '&': { fontSize: '0.8rem' },
  '&.cm-focused': { outline: 'none' },
});

interface Props {
  /** True while the signature is locked (published problem) — parsing would only be rejected. */
  disabled: boolean;
  onParsed: (signature: ParsedSignature) => void;
}

/**
 * Paste a LeetCode-style code template and fill the method signature from it — the backend reads
 * the method name, return type and parameters (`POST /admin/problems/parse-template`). The template
 * itself isn't saved: the signature below stays the source of truth, and every language's starter
 * code is regenerated from it, so one Java template gives Python and JavaScript starters too.
 * A parse error is shown inline (it says exactly what to fix), not as a toast.
 */
export default function CodeTemplateImporter({ disabled, onParsed }: Props): JSX.Element {
  const theme = useTheme();
  const [language, setLanguage] = useState<ProgrammingLanguage>('JAVA');
  const [code, setCode] = useState('');
  const [parsing, setParsing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const extensions = useMemo(() => [chrome, LANGUAGE_EXTENSIONS[language]()], [language]);

  const handleParse = async () => {
    setParsing(true);
    setError(null);
    try {
      onParsed(await devPracticeApi.parseTemplate(language, code));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not read the template');
    } finally {
      setParsing(false);
    }
  };

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
        <Box>
          <Typography variant="subtitle2" fontWeight={700}>Code template</Typography>
          <Typography variant="caption" color="text.secondary">
            Paste the method users implement — the signature below is filled from it.
          </Typography>
        </Box>
        <ToggleButtonGroup
          size="small"
          exclusive
          value={language}
          onChange={(_, v: ProgrammingLanguage | null) => { if (v) { setLanguage(v); setError(null); } }}
          disabled={disabled}
        >
          {LANGUAGES.map(l => <ToggleButton key={l.value} value={l.value}>{l.label}</ToggleButton>)}
        </ToggleButtonGroup>
      </Stack>

      <Box sx={{ border: 1, borderColor: error ? 'error.main' : 'divider', borderRadius: 1, overflow: 'hidden' }}>
        <CodeMirror
          value={code}
          onChange={v => { setCode(v); setError(null); }}
          placeholder={PLACEHOLDERS[language]}
          editable={!disabled}
          theme={theme.palette.mode === 'dark' ? 'dark' : 'light'}
          extensions={extensions}
          minHeight="120px"
          maxHeight="320px"
        />
      </Box>

      {error && <Alert severity="error" sx={{ mt: 1.5 }}>{error}</Alert>}

      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 1.5 }}>
        <Typography variant="caption" color="text.secondary">
          {disabled
            ? 'Locked — set the status to Draft to change a published signature.'
            : 'Replaces the current method name, return type and parameters.'}
        </Typography>
        <SubmitButton
          saving={parsing}
          onClick={handleParse}
          label="Parse template"
          startIcon={<AutoFixHighIcon />}
          disabled={disabled || !code.trim()}
        />
      </Stack>
    </Paper>
  );
}
