import { java } from '@codemirror/lang-java';
import { python } from '@codemirror/lang-python';
import { javascript } from '@codemirror/lang-javascript';
import { EditorView } from '@codemirror/view';
import { useTheme } from '@mui/material';
import { ProgrammingLanguage } from '../types';

/** CodeMirror highlighting per submission language — shared by every code editor in this feature
 * so the same language is never highlighted differently. */
export const LANGUAGE_EXTENSIONS: Record<ProgrammingLanguage, typeof java> = {
  JAVA: java,
  PYTHON: python,
  JAVASCRIPT: javascript,
};

/**
 * The editor chrome every CodeMirror in this feature uses: a given font size, and no dotted focus
 * outline — the wrapping box draws the border instead. `extra` adds per-editor rules (e.g. a field's
 * tighter padding).
 */
export function editorChrome(fontSize: string, extra: Parameters<typeof EditorView.theme>[0] = {}) {
  return EditorView.theme({ '&': { fontSize }, '&.cm-focused': { outline: 'none' }, ...extra });
}

/** CodeMirror's built-in light/dark theme, following the app's MUI palette mode. */
export function useCodeMirrorTheme(): 'light' | 'dark' {
  return useTheme().palette.mode === 'dark' ? 'dark' : 'light';
}
