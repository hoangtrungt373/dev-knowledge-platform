import { useMemo } from 'react';
import { Box, FormHelperText, Typography } from '@mui/material';
import CodeMirror from '@uiw/react-codemirror';
import { json } from '@codemirror/lang-json';
import { EditorView } from '@codemirror/view';
import { editorChrome, useCodeMirrorTheme } from '../utils/codeLanguages';

// Matches a small MUI TextField's own text size/inset, so a JSON field sits naturally next to the
// plain TextFields on the same form. `outline: none` drops CodeMirror's own dotted focus box — the
// wrapping Box draws the focus/error border instead (same fix @dev-utils' editorChromeTheme makes).
const fieldChrome = editorChrome('0.8rem', {
  '.cm-content': { padding: '8px 0' },
  '.cm-line': { padding: '0 10px' },
});

interface Props {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  error?: string;
  /** Shown under the field when there's no error. */
  helperText?: string;
  disabled?: boolean;
}

/**
 * A small JSON editor for one test-case value — CodeMirror with JSON highlighting, styled to read
 * like an outlined MUI field (label above, border that turns red on error). Line numbers and
 * folding are off: these values are short, usually one line.
 */
export default function JsonCodeField({
  label,
  value,
  onChange,
  placeholder,
  error,
  helperText,
  disabled,
}: Props): JSX.Element {
  const cmTheme = useCodeMirrorTheme();
  const extensions = useMemo(() => [fieldChrome, json(), EditorView.lineWrapping], []);

  return (
    <Box>
      <Typography
        variant="caption"
        fontWeight={600}
        color={error ? 'error.main' : 'text.secondary'}
        sx={{ display: 'block', mb: 0.5 }}
      >
        {label}
      </Typography>
      <Box
        sx={{
          border: 1,
          borderColor: error ? 'error.main' : 'divider',
          borderRadius: 1,
          overflow: 'hidden',
          '&:focus-within': { borderColor: error ? 'error.main' : 'primary.main' },
        }}
      >
        <CodeMirror
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          editable={!disabled}
          theme={cmTheme}
          extensions={extensions}
          basicSetup={{ lineNumbers: false, foldGutter: false, highlightActiveLine: false }}
          minHeight="36px"
          maxHeight="180px"
        />
      </Box>
      {(error || helperText) && (
        <FormHelperText error={Boolean(error)} sx={{ mx: 0 }}>
          {error || helperText}
        </FormHelperText>
      )}
    </Box>
  );
}
