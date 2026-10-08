import { useState } from 'react';
import { Alert, Chip, Stack, Typography } from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import { MethodParameter, RunCaseResult, RunResult } from '../types';
import { SUBMISSION_STATUS_LABEL } from '../constants';
import { argumentLines } from '../utils/runCases';
import CodeBlock from './CodeBlock';

interface Props {
  result: RunResult;
  parameters: MethodParameter[];
}

/**
 * The headline of a whole run: the first failure if any case failed to run or answered wrongly,
 * otherwise "Accepted" when every answer was checked, or just "Finished" when some inputs had no
 * known answer (custom input — nothing to judge them against).
 */
function headline(result: RunResult): { label: string; color: 'success' | 'error' | 'info' } {
  const failure = result.cases.find(c => c.status !== 'ACCEPTED');
  if (failure) return { label: SUBMISSION_STATUS_LABEL[failure.status], color: 'error' };
  if (result.cases.every(c => c.passed === true)) return { label: 'Accepted', color: 'success' };
  return { label: 'Finished', color: 'info' };
}

/**
 * One case's outcome: passed / failed against a known answer, failed to run at all (compile or
 * runtime error, time limit), or ran fine with no answer to compare to (an edited custom input).
 */
function outcomeOf(c: RunCaseResult): 'passed' | 'failed' | 'unchecked' {
  if (c.passed === true) return 'passed';
  if (c.passed === false || c.status !== 'ACCEPTED') return 'failed';
  return 'unchecked';
}

const OUTCOME_CHIP = {
  passed: { icon: <CheckIcon />, color: 'success' },
  failed: { icon: <CloseIcon />, color: 'error' },
  unchecked: { icon: undefined, color: 'default' },
} as const;

/**
 * The Run console's result: a headline, a chip per case (✓ passed, ✗ failed, plain when there was
 * no answer to check), and the selected case's input, output, expected answer and any diagnostic
 * (compiler output, stack trace). Nothing here is saved — it's the learner's scratch area.
 */
export default function RunResultView({ result, parameters }: Props): JSX.Element {
  const [selected, setSelected] = useState(0);
  if (result.cases.length === 0) {
    return <Typography variant="body2" color="text.secondary">This problem has no sample cases — add your own in the Testcase tab.</Typography>;
  }
  const { label, color } = headline(result);
  const current = result.cases[Math.min(selected, result.cases.length - 1)];

  return (
    <Stack spacing={1.5}>
      <Typography variant="subtitle1" fontWeight={700} color={`${color}.main`}>{label}</Typography>

      <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
        {result.cases.map((c, i) => (
          <Chip
            key={i}
            size="small"
            label={`Case ${i + 1}`}
            {...OUTCOME_CHIP[outcomeOf(c)]}
            variant={i === selected ? 'filled' : 'outlined'}
            onClick={() => setSelected(i)}
          />
        ))}
      </Stack>

      <CodeBlock label="Input">{argumentLines(current.input, parameters).join('\n')}</CodeBlock>
      {current.status === 'ACCEPTED' || current.status === 'WRONG_ANSWER' ? (
        <CodeBlock label="Output">{current.actualOutput?.trim() || '(nothing returned)'}</CodeBlock>
      ) : (
        <Alert severity="error" sx={{ py: 0 }}>{SUBMISSION_STATUS_LABEL[current.status]}</Alert>
      )}
      {current.expectedOutput !== null && <CodeBlock label="Expected">{current.expectedOutput}</CodeBlock>}
      {current.diagnostic && <CodeBlock label="Details">{current.diagnostic}</CodeBlock>}
    </Stack>
  );
}
