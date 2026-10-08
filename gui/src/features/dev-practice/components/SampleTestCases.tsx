import { Box, Paper, Stack, Typography } from '@mui/material';
import { MethodParameter, TestCase } from '../types';
import { argumentLines } from '../utils/runCases';

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
          <Paper key={tc.id} variant="outlined" sx={{ p: 1.5, bgcolor: 'action.hover' }}>
            <Typography variant="caption" color="text.secondary" fontWeight={700}>Case {i + 1}</Typography>
            <Box component="pre" sx={{ m: 0, mt: 0.5, fontFamily: 'monospace', fontSize: '0.8rem', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
              {argumentLines(tc.input, parameters).join('\n')}
            </Box>
            <Typography variant="caption" color="text.secondary" fontWeight={700} component="div" sx={{ mt: 1 }}>
              Expected
            </Typography>
            <Box component="pre" sx={{ m: 0, fontFamily: 'monospace', fontSize: '0.8rem', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
              {tc.expectedOutput}
            </Box>
          </Paper>
        ))}
      </Stack>
    </Box>
  );
}
