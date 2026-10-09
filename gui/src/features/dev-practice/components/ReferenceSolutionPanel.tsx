import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  AlertColor,
  Box,
  Button,
  Checkbox,
  Chip,
  Divider,
  FormControlLabel,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import VerifiedIcon from '@mui/icons-material/Verified';
import { Problem, Submission } from '../types';
import { devPracticeApi } from '../api/devPracticeApi';
import { LANGUAGE_LABEL, SUBMISSION_STATUS_LABEL } from '../constants';
import { isInProgress, useSubmissionPolling } from '../hooks/useSubmissionPolling';
import { useSolutionDrafts } from '../hooks/useSolutionDrafts';
import { formatDateTime, submissionStats } from '../utils/format';
import SolutionEditor from './SolutionEditor';
import SubmissionStatusChip from './SubmissionStatusChip';
import CodeBlock from './CodeBlock';
import { useNotification } from '@shared/contexts/NotificationContext';
import SubmitButton from '@shared/components/SubmitButton';

const HISTORY_SIZE = 5;

/** Which saved version of the problem a past run was judged against. */
function versionLabel(judgedVersion: number | null, currentVersion: number): string {
  if (judgedVersion === null) return 'not judged yet';
  return `v${judgedVersion} (${judgedVersion === currentVersion ? 'current' : 'outdated'})`;
}

interface Props {
  /** The problem as last saved — references are always judged against the saved version. */
  problem: Problem;
  /** True while the form holds signature/test-data edits that aren't saved yet. */
  contractDirty: boolean;
  /**
   * Called once a reference is ACCEPTED at the problem's current `contractVersion`.
   * `publishRequested` = the run was submitted with "publish if accepted", so the backend may have
   * just published the problem (it skips that if the problem stopped being a draft meanwhile).
   */
  onVerified: (publishRequested: boolean) => void;
}

/**
 * The admin's "prove this problem is solvable" step, required before publishing (DKP-0056): write a
 * solution, submit it as a REFERENCE submission, and watch the judge's verdict. The backend judges
 * asynchronously, so a submission comes back PENDING and this panel polls it until the status is
 * final. Starter code is pre-filled from the problem's *saved* signature; an editor still holding
 * an untouched starter is refreshed when that signature changes, but typed code is never replaced.
 */
export default function ReferenceSolutionPanel({ problem, contractDirty, onVerified }: Props): JSX.Element {
  const { showError } = useNotification();
  // Not persisted (no storageKey): a reference is a one-off verification, not a learner's draft.
  const drafts = useSolutionDrafts({
    loadStarter: language => devPracticeApi.getStarterCode(problem.id, language).then(s => s.code),
    // A new contract version means a new saved signature: re-fetch the starters.
    starterVersion: problem.contractVersion,
  });
  const { language, code } = drafts;
  const [submitting, setSubmitting] = useState(false);
  const [history, setHistory] = useState<Submission[]>([]);
  const [publishOnAccept, setPublishOnAccept] = useState(false);
  // Publish-on-accept only means something for a draft (the backend ignores it otherwise).
  const canPublishOnAccept = problem.status === 'DRAFT';
  const verified = Boolean(problem.verified) && !contractDirty;

  const loadHistory = useCallback(() => {
    devPracticeApi.listReferenceSubmissions(problem.id, HISTORY_SIZE)
      .then(page => setHistory(page.content))
      .catch(() => { /* cosmetic — the panel works without it */ });
  }, [problem.id]);

  useEffect(loadHistory, [loadHistory]);

  const { submission: current, gaveUp: pollGaveUp, track } = useSubmissionPolling(
    id => devPracticeApi.getReferenceSubmission(problem.id, id),
    next => {
      loadHistory();
      if (next.status === 'ACCEPTED' && next.contractVersion === problem.contractVersion) {
        onVerified(Boolean(next.publishOnAccept));
      }
    },
  );

  const handleRun = async () => {
    setSubmitting(true);
    try {
      track(await devPracticeApi.createReferenceSubmission(
        problem.id, language, code, canPublishOnAccept && publishOnAccept, showError,
      ));
    } catch {
      // showError already called
    } finally {
      setSubmitting(false);
    }
  };

  const judging = submitting || (isInProgress(current) && !pollGaveUp);
  const runDisabled = contractDirty || !code.trim() || judging;

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
        <Box>
          <Typography variant="subtitle2" fontWeight={700}>Reference solution</Typography>
          <Typography variant="caption" color="text.secondary">
            A problem can only be published once a solution passes every test case of its saved version.
          </Typography>
        </Box>
        {verified
          ? <Chip icon={<VerifiedIcon />} label="Verified" color="success" size="small" />
          : <Chip label="Not verified" size="small" variant="outlined" />}
      </Stack>

      {contractDirty && (
        <Alert severity="warning" sx={{ mb: 1.5 }}>
          The signature or test cases have unsaved changes. Save the problem first — a reference
          solution is always judged against the saved version.
        </Alert>
      )}

      <SolutionEditor
        language={language}
        onLanguageChange={drafts.setLanguage}
        code={code}
        onCodeChange={drafts.setCode}
      />

      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 1.5 }}>
        <Box>
          <Typography variant="caption" color="text.secondary" component="div">
            Judged against all {problem.testCases.length} saved test case(s), hidden ones included.
          </Typography>
          {/* Draft only: the backend publishes the problem itself once the run is ACCEPTED, re-checking
              it's still a draft at the judged version — so an edit made while judging wins. */}
          {canPublishOnAccept && (
            <FormControlLabel
              control={(
                <Checkbox
                  size="small"
                  checked={publishOnAccept}
                  onChange={e => setPublishOnAccept(e.target.checked)}
                  disabled={judging}
                />
              )}
              label={<Typography variant="body2">Publish if accepted</Typography>}
            />
          )}
        </Box>
        <SubmitButton
          saving={judging}
          onClick={handleRun}
          label={canPublishOnAccept && publishOnAccept ? 'Run & publish' : 'Run reference'}
          startIcon={<PlayArrowIcon />}
          disabled={runDisabled}
        />
      </Stack>

      {current && (
        <SubmissionResult
          submission={current}
          pollGaveUp={pollGaveUp}
          contractVersion={problem.contractVersion}
          problemStatus={problem.status}
        />
      )}

      {history.length > 0 && (
        <>
          <Divider sx={{ my: 2 }} />
          <Typography variant="caption" color="text.secondary" fontWeight={700}>Recent runs</Typography>
          <Stack spacing={0.75} sx={{ mt: 1 }}>
            {history.map(s => (
              <Stack key={s.id} direction="row" alignItems="center" spacing={1}>
                <SubmissionStatusChip status={s.status} />
                <Typography variant="body2" sx={{ minWidth: 80 }}>{LANGUAGE_LABEL[s.language]}</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
                  {versionLabel(s.contractVersion, problem.contractVersion)}
                  {' · '}{formatDateTime(s.submittedAt)}
                </Typography>
                <Button
                  size="small"
                  onClick={() => {
                    drafts.setLanguage(s.language);
                    drafts.setCodeFor(s.language, s.sourceCode);
                  }}
                >
                  Load code
                </Button>
              </Stack>
            ))}
          </Stack>
        </>
      )}
    </Paper>
  );
}

/** What an ACCEPTED reference run did to the problem, given the status the page now shows. */
function acceptedOutcome(publishRequested: boolean, problemStatus: Problem['status']): string {
  if (!publishRequested) return 'The problem is verified. You can now set its status to Published and save.';
  switch (problemStatus) {
    case 'PUBLISHED':
      return 'The problem is verified and has been published.';
    case 'DRAFT':
      return 'The problem is verified — publishing…';
    case 'ARCHIVED':
      return 'The problem is verified, but it was not published: it is no longer a draft.';
  }
}

/** Accepted at the current version = success; accepted against an old version, or a judge failure
 * (not a verdict on the code) = warning; anything else = the code is wrong. */
function resultSeverity(submission: Submission, stale: boolean): AlertColor {
  if (submission.status === 'ACCEPTED') return stale ? 'warning' : 'success';
  return submission.status === 'JUDGE_ERROR' ? 'warning' : 'error';
}

/** The verdict of the run just started from this panel. */
function SubmissionResult({ submission, pollGaveUp, contractVersion, problemStatus }: {
  submission: Submission;
  pollGaveUp: boolean;
  contractVersion: number;
  /** The page refetches the problem after a publish-on-accept run, so this shows the real outcome. */
  problemStatus: Problem['status'];
}): JSX.Element {
  if (isInProgress(submission)) {
    return (
      <Alert severity="info" sx={{ mt: 1.5 }}>
        {pollGaveUp
          ? 'Still judging — check the recent runs below later.'
          : `${SUBMISSION_STATUS_LABEL[submission.status]}…`}
      </Alert>
    );
  }
  const passed = submission.passedTestCases ?? 0;
  const total = submission.totalTestCases ?? 0;
  const accepted = submission.status === 'ACCEPTED';
  const stale = submission.contractVersion !== contractVersion;
  return (
    <Alert severity={resultSeverity(submission, stale)} sx={{ mt: 1.5 }}>
      <Typography variant="body2" fontWeight={700}>
        {SUBMISSION_STATUS_LABEL[submission.status]} — {passed}/{total} test cases passed
      </Typography>
      {submissionStats(submission) && <Typography variant="body2">{submissionStats(submission)}</Typography>}
      {accepted && !stale && (
        <Typography variant="body2">{acceptedOutcome(Boolean(submission.publishOnAccept), problemStatus)}</Typography>
      )}
      {accepted && stale && (
        <Typography variant="body2">This run was judged against an older version of the problem — run it again.</Typography>
      )}
      {submission.errorMessage && <CodeBlock variant="plain">{submission.errorMessage}</CodeBlock>}
    </Alert>
  );
}
