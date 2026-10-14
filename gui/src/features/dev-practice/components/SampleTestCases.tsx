import { Box, Paper, Stack, Typography } from '@mui/material';
import { MethodParameter, OutputOrder, TestCase } from '../types';
import { argumentLines } from '../utils/runCases';
import CodeBlock from './CodeBlock';

interface Props {
  parameters: MethodParameter[];
  /** Sample cases only — the public API never sends hidden ones. */
  testCases: TestCase[];
  /** `ANY_ORDER` adds a note that the expected answers are one of several accepted orders. */
  outputOrder?: OutputOrder;
}

/** The problem's sample test cases, written as named arguments and the expected return value. */
export default function SampleTestCases({ parameters, testCases, outputOrder }: Props): JSX.Element | null {
  if (testCases.length === 0) return null;
  return (
    <Box>
      <Typography variant="subtitle2" fontWeight={700} sx={{ mb: outputOrder === 'ANY_ORDER' ? 0.5 : 1 }}>Sample test cases</Typography>
      {outputOrder === 'ANY_ORDER' && (
        <Typography variant="caption" color="text.secondary" component="p" sx={{ mb: 1 }}>
          Any order is accepted — the expected answers below show one of them.
        </Typography>
      )}
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
