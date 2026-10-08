import { Alert, AlertColor, Box, LinearProgress, Typography } from '@mui/material';
import { Submission } from '../types';
import { SUBMISSION_STATUS_LABEL } from '../constants';
import { isInProgress } from '../hooks/useSubmissionPolling';

interface Props {
  submission: Submission;
  /** Polling stopped while the submission was still judging. */
  gaveUp: boolean;
}

/**
 * One-line explanation of a final verdict. The judge stops at the first failing test, so for a
 * failure `passedTestCases + 1` is the test it failed on — named by number only: a hidden test's
 * input is never shown, the same way LeetCode reports "Wrong Answer on test 7".
 */
function describe(s: Submission): string {
  const total = s.totalTestCases ?? 0;
  const passed = s.passedTestCases ?? 0;
  const failedOn = `test ${passed + 1} of ${total}`;
  switch (s.status) {
    case 'ACCEPTED':
      return `All ${total} test cases passed.`;
    case 'WRONG_ANSWER':
      return `Wrong answer on ${failedOn} — ${passed} passed before it.`;
    case 'RUNTIME_ERROR':
      return `Your code crashed on ${failedOn}.`;
    case 'TIME_LIMIT_EXCEEDED':
      return `Too slow on ${failedOn} — look for a faster approach.`;
    case 'COMPILE_ERROR':
      return 'Your code did not compile.';
    case 'JUDGE_ERROR':
      return 'The judge could not run your code this time — this is not a verdict on it. Please submit again.';
    case 'PENDING':
    case 'RUNNING':
      return '';
  }
}

const SEVERITY: Record<Submission['status'], AlertColor> = {
  PENDING: 'info',
  RUNNING: 'info',
  ACCEPTED: 'success',
  WRONG_ANSWER: 'error',
  COMPILE_ERROR: 'error',
  RUNTIME_ERROR: 'error',
  TIME_LIMIT_EXCEEDED: 'error',
  JUDGE_ERROR: 'warning',
};

/** The learner's verdict panel: a progress bar while judging, then the result and any error output. */
export default function SubmissionVerdict({ submission, gaveUp }: Props): JSX.Element {
  if (isInProgress(submission)) {
    return (
      <Alert severity="info" icon={false} sx={{ '& .MuiAlert-message': { width: '100%' } }}>
        <Typography variant="body2" fontWeight={700}>
          {gaveUp ? 'Still judging…' : `${SUBMISSION_STATUS_LABEL[submission.status]}…`}
        </Typography>
        {gaveUp ? (
          <Typography variant="body2">
            This is taking longer than usual. The result will appear in the Submissions tab once it lands.
          </Typography>
        ) : (
          <LinearProgress sx={{ mt: 1 }} />
        )}
      </Alert>
    );
  }
  return (
    <Alert severity={SEVERITY[submission.status]}>
      <Typography variant="body2" fontWeight={700}>{SUBMISSION_STATUS_LABEL[submission.status]}</Typography>
      <Typography variant="body2">{describe(submission)}</Typography>
      {submission.errorMessage && (
        <Box
          component="pre"
          sx={{ m: 0, mt: 1, whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontFamily: 'monospace', fontSize: '0.75rem' }}
        >
          {submission.errorMessage}
        </Box>
      )}
    </Alert>
  );
}
