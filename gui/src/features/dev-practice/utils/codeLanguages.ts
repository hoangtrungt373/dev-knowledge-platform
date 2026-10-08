import { java } from '@codemirror/lang-java';
import { python } from '@codemirror/lang-python';
import { javascript } from '@codemirror/lang-javascript';
import { ProgrammingLanguage } from '../types';

/** CodeMirror highlighting per submission language — shared by the template importer and the
 * reference-solution editor so the two never highlight a language differently. */
export const LANGUAGE_EXTENSIONS: Record<ProgrammingLanguage, typeof java> = {
  JAVA: java,
  PYTHON: python,
  JAVASCRIPT: javascript,
};
