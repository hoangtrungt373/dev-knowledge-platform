import { Box, Paper, Stack, Typography } from '@mui/material';
import { MethodParameter, TestCase } from '../types';
import { argumentLines } from '../utils/runCases';
import CodeBlock from './CodeBlock';

interface Props {
  parameters: MethodParameter[];
  /** Sample cases only — the public API never sends hidden ones. */
  testCases: TestCase[];
}

/** The problem's sample test cases, written as named arguments and the expected return value. */
export default function SampleTestCases({ parameters, testCases }: Props): JSX.Element | null {
  if (testCases.length === 0) return null;
  return (
    <Box>
      <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1 }}>Sample test cases</Typography>
      <Stack spacing={1}>
        {testCases.map((tc, i) => (
          <Paper key={tc.id} variant="outlined" sx={{ p: 1.5 }}>
            <Typography variant="body2" fontWeight={700} sx={{ mb: 1 }}>Case {i + 1}</Typography>
            <Stack spacing={1}>
              <CodeBlock label="Input">{argumentLines(tc.input, parameters).join('\n')}</CodeBlock>
              <CodeBlock label="Expected">{tc.expectedOutput}</CodeBlock>
            </Stack>
          </Paper>
        ))}
      </Stack>
    </Box>
  );
}
