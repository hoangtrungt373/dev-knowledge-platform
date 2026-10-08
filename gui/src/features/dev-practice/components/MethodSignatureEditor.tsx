import {
  Alert,
  Box,
  Button,
  FormControl,
  FormHelperText,
  IconButton,
  InputLabel,
  ListSubheader,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import DeleteIcon from '@mui/icons-material/Delete';
import LockIcon from '@mui/icons-material/Lock';
import { ParamType } from '../types';
import { PARAM_TYPE_LABEL, PARAM_TYPES } from '../constants';
import { nextRowKey, ParamRow, ProblemFormErrors, SignatureTypeHints, TypeHint } from '../utils/problemForm';

interface Props {
  methodName: string;
  onMethodNameChange: (value: string) => void;
  returnType: ParamType;
  onReturnTypeChange: (value: ParamType) => void;
  params: ParamRow[];
  onParamsChange: (rows: ParamRow[]) => void;
  /** True while the problem is PUBLISHED and staying PUBLISHED — the backend rejects any change. */
  locked: boolean;
  /** Restores the signature as last loaded, offered when a locked signature was edited anyway. */
  onRevert?: () => void;
  errors: ProblemFormErrors;
  /** Shown under a type picker while it still holds the template's guessed type. */
  typeHints?: SignatureTypeHints;
}

/** A type picker listing each ParamType with a JSON example, so the admin knows how test data
 * for that type has to look. */
function TypeSelect({ label, value, onChange, disabled, hint }: {
  label: string;
  value: ParamType;
  onChange: (value: ParamType) => void;
  disabled: boolean;
  hint?: TypeHint;
}): JSX.Element {
  // Only while the admin hasn't changed it — picking another type is the confirmation.
  const showHint = hint && hint.alternatives.length > 0 && hint.chosen === value;
  return (
    <FormControl size="small" sx={{ minWidth: 150 }} disabled={disabled}>
      <InputLabel>{label}</InputLabel>
      <Select
        label={label}
        value={value}
        onChange={e => onChange(e.target.value as ParamType)}
        renderValue={v => <Box component="span" sx={{ fontFamily: 'monospace' }}>{PARAM_TYPE_LABEL[v]}</Box>}
      >
        <ListSubheader>Type — example JSON value</ListSubheader>
        {PARAM_TYPES.map(t => (
          <MenuItem key={t.value} value={t.value}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', width: '100%', gap: 3 }}>
              <Box component="span" sx={{ fontFamily: 'monospace' }}>{t.label}</Box>
              <Typography component="span" variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                {t.example}
              </Typography>
            </Box>
          </MenuItem>
        ))}
      </Select>
      {showHint && (
        <FormHelperText sx={{ color: 'warning.main', mx: 0, maxWidth: 180 }}>
          Guessed from the template — could also be {hint.alternatives.map(t => PARAM_TYPE_LABEL[t]).join(' or ')}
        </FormHelperText>
      )}
    </FormControl>
  );
}

/**
 * Edits a problem's method signature: name, return type, and the ordered parameter list. Order
 * matters (it's the argument order every test-case input follows), so rows reorder with up/down
 * buttons — the same convention @ecommerce's ImageThumbnailGrid uses.
 */
export default function MethodSignatureEditor({
  methodName,
  onMethodNameChange,
  returnType,
  onReturnTypeChange,
  params,
  onParamsChange,
  locked,
  onRevert,
  errors,
  typeHints,
}: Props): JSX.Element {
  const updateRow = (key: string, patch: Partial<ParamRow>) =>
    onParamsChange(params.map(p => (p.key === key ? { ...p, ...patch } : p)));

  const moveRow = (index: number, delta: -1 | 1) => {
    const target = index + delta;
    if (target < 0 || target >= params.length) return;
    const next = [...params];
    [next[index], next[target]] = [next[target], next[index]];
    onParamsChange(next);
  };

  const removeRow = (key: string) => onParamsChange(params.filter(p => p.key !== key));
  const addRow = () => onParamsChange([...params, { key: nextRowKey(), name: '', type: 'INT' }]);

  // e.g. "int[] twoSum(int[] nums, int target)" — a quick read of what submitters will implement.
  const preview = `${PARAM_TYPE_LABEL[returnType]} ${methodName.trim() || 'method'}(${params
    .map(p => `${PARAM_TYPE_LABEL[p.type]} ${p.name.trim() || '?'}`)
    .join(', ')})`;

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1.5 }}>
        <Typography variant="subtitle2" fontWeight={700}>Method signature</Typography>
        {locked && <LockIcon fontSize="small" color="action" />}
      </Stack>

      {locked && (
        <Alert severity="info" sx={{ mb: 2 }}>
          This problem is published, so its signature is locked — changing it would break every
          existing submission and test case. Set the status to <strong>Draft</strong> to edit it.
        </Alert>
      )}
      {errors.signature && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={onRevert && <Button color="inherit" size="small" onClick={onRevert}>Revert</Button>}
        >
          {errors.signature}
        </Alert>
      )}

      <Stack direction="row" spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          label="Method name"
          size="small"
          value={methodName}
          onChange={e => onMethodNameChange(e.target.value)}
          error={Boolean(errors.methodName)}
          helperText={errors.methodName || 'e.g. twoSum'}
          disabled={locked}
          required
          sx={{ flex: 1 }}
          inputProps={{ style: { fontFamily: 'monospace' } }}
        />
        <TypeSelect
          label="Return type"
          value={returnType}
          onChange={onReturnTypeChange}
          disabled={locked}
          hint={typeHints?.returnType}
        />
      </Stack>

      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ display: 'block', mb: 1 }}>
        Parameters (in argument order)
      </Typography>
      <Stack spacing={1}>
        {params.map((p, index) => (
          <Stack key={p.key} direction="row" spacing={1} alignItems="flex-start">
            <Typography variant="body2" color="text.secondary" sx={{ width: 20, pt: 1, textAlign: 'right' }}>
              {index + 1}
            </Typography>
            <TextField
              label="Name"
              size="small"
              value={p.name}
              onChange={e => updateRow(p.key, { name: e.target.value })}
              error={Boolean(errors.paramNames[p.key])}
              helperText={errors.paramNames[p.key]}
              disabled={locked}
              sx={{ flex: 1 }}
              inputProps={{ style: { fontFamily: 'monospace' } }}
            />
            <TypeSelect
              label="Type"
              value={p.type}
              onChange={type => updateRow(p.key, { type })}
              disabled={locked}
              hint={typeHints?.params[p.key]}
            />
            <Tooltip title="Move up">
              <span>
                <IconButton size="small" onClick={() => moveRow(index, -1)} disabled={locked || index === 0}>
                  <ArrowUpwardIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            <Tooltip title="Move down">
              <span>
                <IconButton
                  size="small"
                  onClick={() => moveRow(index, 1)}
                  disabled={locked || index === params.length - 1}
                >
                  <ArrowDownwardIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            <Tooltip title="Remove">
              <span>
                <IconButton size="small" color="error" onClick={() => removeRow(p.key)} disabled={locked}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          </Stack>
        ))}
      </Stack>
      {errors.parameters && <FormHelperText error>{errors.parameters}</FormHelperText>}

      <Button size="small" startIcon={<AddIcon />} onClick={addRow} disabled={locked} sx={{ mt: 1 }}>
        Add parameter
      </Button>

      <Box sx={{ mt: 2, p: 1.5, bgcolor: 'action.hover', borderRadius: 1 }}>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>Preview</Typography>
        <Typography variant="body2" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>{preview}</Typography>
      </Box>
    </Paper>
  );
}
