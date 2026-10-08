import { ToggleButton, ToggleButtonGroup } from '@mui/material';
import { ProgrammingLanguage } from '../types';
import { LANGUAGES } from '../constants';

interface Props {
  value: ProgrammingLanguage;
  onChange: (language: ProgrammingLanguage) => void;
  disabled?: boolean;
}

/** Java / Python / JavaScript as an exclusive toggle; clicking the selected one keeps it selected. */
export default function LanguageToggle({ value, onChange, disabled }: Props): JSX.Element {
  return (
    <ToggleButtonGroup
      size="small"
      exclusive
      value={value}
      disabled={disabled}
      onChange={(_, v: ProgrammingLanguage | null) => { if (v) onChange(v); }}
    >
      {LANGUAGES.map(l => <ToggleButton key={l.value} value={l.value}>{l.label}</ToggleButton>)}
    </ToggleButtonGroup>
  );
}
