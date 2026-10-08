import { Box, Paper, Stack, Typography } from '@mui/material';
import { MethodParameter, TestCase } from '../types';

interface Props {
  parameters: MethodParameter[];
  /** Sample cases only — the public API never sends hidden ones. */
  testCases: TestCase[];
}

/**
 * Turns a test case's JSON argument array into `name = value` lines, in parameter order — the way a
 * problem statement writes an example (`nums = [1,2,3,3]`), rather than the raw `[[1,2,3,3]]` the
 * judge stores. Falls back to the raw text if it doesn't parse as one value per parameter.
 */
function argumentLines(input: string, parameters: MethodParameter[]): string[] {
  try {
    const args: unknown = JSON.parse(input);
    if (Array.isArray(args) && args.length === parameters.length) {
      const ordered = [...parameters].sort((a, b) => a.position - b.position);
      return ordered.map((p, i) => `${p.name} = ${JSON.stringify(args[i])}`);
    }
  } catch {
    // not JSON — show as stored
  }
  return [input];
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
