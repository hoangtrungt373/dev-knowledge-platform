import {
  Box,
  Button,
  Chip,
  FormControlLabel,
  FormHelperText,
  IconButton,
  Paper,
  Stack,
  Switch,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { ParamType } from '../types';
import { PARAM_TYPES } from '../constants';
import { argumentCount, nextRowKey, ParamRow, ProblemFormErrors, TestCaseRow } from '../utils/problemForm';
import JsonCodeField from './JsonCodeField';

interface Props {
  rows: TestCaseRow[];
  onChange: (rows: TestCaseRow[]) => void;
  /** The current signature — drives the per-row argument-count hint and the placeholders. */
  params: ParamRow[];
  returnType: ParamType;
  errors: ProblemFormErrors;
}

function exampleFor(type: ParamType): string {
  return PARAM_TYPES.find(t => t.value === type)?.example ?? '0';
}

/**
 * Edits a problem's test cases inline, one card per case: input (a JSON array of arguments, in
 * parameter order), expected output (one JSON value), and whether it's a public sample. Every card
 * stays visible so the whole set can be scanned at once; a wrong argument count shows right on the
 * card it belongs to.
 */
export default function TestCaseEditor({ rows, onChange, params, returnType, errors }: Props): JSX.Element {
  const updateRow = (key: string, patch: Partial<TestCaseRow>) =>
    onChange(rows.map(r => (r.key === key ? { ...r, ...patch } : r)));
  const removeRow = (key: string) => onChange(rows.filter(r => r.key !== key));
  // The first case defaults to a sample, so a new problem has a worked example unless the admin
  // opts out; later ones default to hidden.
  const addRow = () =>
    onChange([...rows, { key: nextRowKey(), input: '', expectedOutput: '', sample: rows.length === 0 }]);

  const inputPlaceholder = `[${params.map(p => exampleFor(p.type)).join(', ')}]`;
  const outputPlaceholder = exampleFor(returnType);
  const argNames = params.map(p => p.name.trim() || '?').join(', ');
  const sampleCount = rows.filter(r => r.sample).length;

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1.5 }}>
        <Box>
          <Typography variant="subtitle2" fontWeight={700}>Test cases</Typography>
          <Typography variant="caption" color="text.secondary">
            {rows.length} total · {sampleCount} sample · {rows.length - sampleCount} hidden
          </Typography>
        </Box>
        <Button size="small" startIcon={<AddIcon />} onClick={addRow}>Add test case</Button>
      </Stack>

      <Stack spacing={1.5}>
        {rows.map((row, index) => {
          const count = argumentCount(row.input);
          return (
            <Paper key={row.key} variant="outlined" sx={{ p: 1.5, bgcolor: 'background.default' }}>
              <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
                <Stack direction="row" alignItems="center" spacing={1}>
                  <Typography variant="body2" fontWeight={700}>#{index + 1}</Typography>
                  <Chip
                    size="small"
                    variant="outlined"
                    label={row.sample ? 'Sample' : 'Hidden'}
                    color={row.sample ? 'primary' : 'default'}
                  />
                </Stack>
                <Stack direction="row" alignItems="center" spacing={1}>
                  <FormControlLabel
                    control={
                      <Switch
                        size="small"
                        checked={row.sample}
                        onChange={e => updateRow(row.key, { sample: e.target.checked })}
                      />
                    }
                    label={<Typography variant="body2">Show as example</Typography>}
                  />
                  <Tooltip title="Remove test case">
                    <IconButton size="small" color="error" onClick={() => removeRow(row.key)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>
              </Stack>

              <Stack spacing={1.5}>
                <JsonCodeField
                  label={`Input — [${argNames}]`}
                  value={row.input}
                  onChange={input => updateRow(row.key, { input })}
                  placeholder={inputPlaceholder}
                  error={errors.testCaseInputs[row.key]}
                  helperText={count === null ? undefined : `${count} of ${params.length} argument(s)`}
                />
                <JsonCodeField
                  label="Expected output"
                  value={row.expectedOutput}
                  onChange={expectedOutput => updateRow(row.key, { expectedOutput })}
                  placeholder={outputPlaceholder}
                  error={errors.testCaseOutputs[row.key]}
                />
              </Stack>
            </Paper>
          );
        })}
      </Stack>

      {rows.length === 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
          No test cases yet. Add at least one.
        </Typography>
      )}
      {errors.testCases && <FormHelperText error>{errors.testCases}</FormHelperText>}
    </Paper>
  );
}
