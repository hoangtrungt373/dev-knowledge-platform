import { ToggleButton, ToggleButtonGroup } from '@mui/material';
import { ProgrammingLanguage } from '../types';
import { LANGUAGES } from '../constants';

interface Props {
  value: ProgrammingLanguage;
  onChange: (language: ProgrammingLanguage) => void;
  disabled?: boolean;
  /**
   * Only offer these (in the usual order); default every language. A `value` outside them shows no
   * button selected — e.g. the Solution tab when an editorial has no code in the learner's language.
   */
  languages?: ProgrammingLanguage[];
}

/** Java / Python / JavaScript as an exclusive toggle; clicking the selected one keeps it selected. */
export default function LanguageToggle({ value, onChange, disabled, languages }: Props): JSX.Element {
  return (
    <ToggleButtonGroup
      size="small"
      exclusive
      value={value}
      disabled={disabled}
      onChange={(_, v: ProgrammingLanguage | null) => { if (v) onChange(v); }}
    >
      {LANGUAGES.filter(l => !languages || languages.includes(l.value))
        .map(l => <ToggleButton key={l.value} value={l.value}>{l.label}</ToggleButton>)}
    </ToggleButtonGroup>
  );
}
