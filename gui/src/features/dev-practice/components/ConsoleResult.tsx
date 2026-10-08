import { Alert, Box, LinearProgress, Typography } from '@mui/material';
import { MethodParameter, Submission } from '../types';
import { RunOutcome } from '../hooks/useCodeRun';
import SubmissionVerdict from './SubmissionVerdict';
import RunResultView from './RunResultView';

interface Props {
  /** Which action the learner started last — the tab shows that one's result. */
  lastAction: 'run' | 'submit' | null;
  running: boolean;
  runOutcome: RunOutcome | null;
  submission: Submission | null;
  /** Polling stopped while the submission was still judging. */
  gaveUp: boolean;
  parameters: MethodParameter[];
}

/** The workspace console's Result tab: whichever of Run or Submit the learner started last. */
export default function ConsoleResult({ lastAction, running, runOutcome, submission, gaveUp, parameters }: Props): JSX.Element {
  if (lastAction === 'submit' && submission) {
    return <SubmissionVerdict submission={submission} gaveUp={gaveUp} />;
  }
  if (lastAction === 'run') {
    if (running) {
      return (
        <Box>
          <Typography variant="body2" fontWeight={700}>Running your cases…</Typography>
          <LinearProgress sx={{ mt: 1 }} />
        </Box>
      );
    }
    if (runOutcome?.kind === 'error') return <Alert severity="error">{runOutcome.message}</Alert>;
    if (runOutcome?.kind === 'result') {
      // Keyed per run so the selected-case chip starts again at Case 1 for each new result.
      return <RunResultView key={runOutcome.runId} result={runOutcome.result} parameters={parameters} />;
    }
  }
  return (
    <Typography variant="body2" color="text.secondary">
      Run your code on the test cases, or submit it to be judged — the result appears here.
    </Typography>
  );
}
