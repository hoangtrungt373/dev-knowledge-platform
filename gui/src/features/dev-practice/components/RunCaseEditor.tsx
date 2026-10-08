import { useState } from 'react';
import { Box, Button, Chip, IconButton, Stack, TextField, Tooltip, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import { MethodParameter } from '../types';
import { orderedParameters } from '../utils/runCases';

interface Props {
  parameters: MethodParameter[];
  /** One entry per case, one JSON value per parameter. */
  cases: string[][];
  onChange: (cases: string[][]) => void;
  /** Puts the problem's sample cases back. */
  onReset: () => void;
  /** The backend runs at most this many inputs at once (`CodeRunServiceImpl.MAX_CUSTOM_INPUTS`). */
  maxCases: number;
  disabled?: boolean;
}

/**
 * The Run console's "Testcase" tab: a chip per case (add, remove, select), and one field per
 * parameter for the selected case — `nums = [1,2,3]` rather than a raw `[[1,2,3]]` array. Each field
 * holds a JSON value; the backend validates the assembled array, and a bad one comes back as an
 * inline message in the result.
 */
export default function RunCaseEditor({ parameters, cases, onChange, onReset, maxCases, disabled }: Props): JSX.Element {
  const [selected, setSelected] = useState(0);
  const params = orderedParameters(parameters);
  const current = Math.min(selected, cases.length - 1);

  const updateValue = (paramIndex: number, value: string) =>
    onChange(cases.map((c, i) => (i === current ? c.map((v, j) => (j === paramIndex ? value : v)) : c)));

  const addCase = () => {
    // A new case starts as a copy of the selected one — usually a small edit away from what's wanted.
    onChange([...cases, cases[current] ? [...cases[current]] : params.map(() => '')]);
    setSelected(cases.length);
  };

  const removeCase = (index: number) => {
    onChange(cases.filter((_, i) => i !== index));
    setSelected(s => Math.max(0, s > index ? s - 1 : Math.min(s, cases.length - 2)));
  };

  return (
    <Box>
      <Stack direction="row" alignItems="center" spacing={0.75} flexWrap="wrap" useFlexGap sx={{ mb: 1.5 }}>
        {cases.map((_, i) => (
          <Chip
            key={i}
            size="small"
            label={`Case ${i + 1}`}
            color={i === current ? 'primary' : 'default'}
            variant={i === current ? 'filled' : 'outlined'}
            onClick={() => setSelected(i)}
            onDelete={cases.length > 1 && !disabled ? () => removeCase(i) : undefined}
          />
        ))}
        <Tooltip title={cases.length >= maxCases ? `At most ${maxCases} cases per run` : 'Add a case'}>
          <span>
            <IconButton size="small" onClick={addCase} disabled={disabled || cases.length >= maxCases}>
              <AddIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Box sx={{ flex: 1 }} />
        <Button size="small" startIcon={<RestartAltIcon />} onClick={() => { onReset(); setSelected(0); }} disabled={disabled}>
          Sample cases
        </Button>
      </Stack>

      {cases.length === 0 ? (
        <Typography variant="body2" color="text.secondary">Add a case to run your code on.</Typography>
      ) : (
        <Stack spacing={1.25}>
          {params.map((p, j) => (
            <TextField
              key={p.name}
              label={`${p.name} =`}
              size="small"
              fullWidth
              value={cases[current]?.[j] ?? ''}
              onChange={e => updateValue(j, e.target.value)}
              disabled={disabled}
              InputProps={{ sx: { fontFamily: 'monospace', fontSize: '0.85rem' } }}
              placeholder="A JSON value, e.g. [1,2,3]"
            />
          ))}
        </Stack>
      )}
    </Box>
  );
}
