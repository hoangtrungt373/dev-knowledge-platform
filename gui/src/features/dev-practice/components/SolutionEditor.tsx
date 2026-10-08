import { ReactNode, useMemo } from 'react';
import { Box, Stack } from '@mui/material';
import CodeMirror from '@uiw/react-codemirror';
import { ProgrammingLanguage } from '../types';
import { editorChrome, LANGUAGE_EXTENSIONS, useCodeMirrorTheme } from '../utils/codeLanguages';
import LanguageToggle from './LanguageToggle';

const chrome = editorChrome('0.85rem');

interface Props {
  language: ProgrammingLanguage;
  onLanguageChange: (language: ProgrammingLanguage) => void;
  code: string;
  onCodeChange: (code: string) => void;
  /**
   * `'fill'` — the editor takes all the height its parent gives it and scrolls internally (the
   * learner workspace's right pane). `'auto'` — grows with the code between 200 and 420px (the admin
   * reference panel, inside a scrolling form).
   */
  sizing?: 'fill' | 'auto';
  /** Extra toolbar controls, rendered left of the language toggle. */
  toolbar?: ReactNode;
  readOnly?: boolean;
}

/**
 * A language toggle (Java / Python / JavaScript) over a CodeMirror editor highlighted for that
 * language — the editing surface shared by the admin reference panel and the learner workspace.
 * Presentational only: which code is shown per language is the caller's state (see `useSolutionDrafts`).
 */
export default function SolutionEditor({
  language,
  onLanguageChange,
  code,
  onCodeChange,
  sizing = 'auto',
  toolbar,
  readOnly = false,
}: Props): JSX.Element {
  const cmTheme = useCodeMirrorTheme();
  const extensions = useMemo(() => [chrome, LANGUAGE_EXTENSIONS[language]()], [language]);
  const fill = sizing === 'fill';

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: 0, ...(fill && { height: '100%' }) }}>
      <Stack direction="row" alignItems="center" justifyContent="flex-end" spacing={1} sx={{ mb: 1 }}>
        {toolbar}
        <LanguageToggle value={language} onChange={onLanguageChange} />
      </Stack>

      <Box
        sx={{
          border: 1,
          borderColor: 'divider',
          borderRadius: 1,
          overflow: 'hidden',
          // In fill mode the editor is absolutely positioned inside this box, so CodeMirror's own
          // height: 100% resolves against a definite size and its scroller engages — a percentage
          // height through a flex chain alone doesn't reliably give it one.
          ...(fill && { flex: 1, minHeight: 0, position: 'relative' }),
        }}
      >
        <CodeMirror
          value={code}
          onChange={onCodeChange}
          readOnly={readOnly}
          theme={cmTheme}
          extensions={extensions}
          {...(fill
            ? { height: '100%', style: { position: 'absolute', inset: 0 } }
            : { minHeight: '200px', maxHeight: '420px' })}
        />
      </Box>
    </Box>
  );
}
