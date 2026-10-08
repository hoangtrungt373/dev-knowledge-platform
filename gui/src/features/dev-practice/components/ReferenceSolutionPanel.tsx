import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Divider,
  FormControlLabel,
  Paper,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
  useTheme,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import VerifiedIcon from '@mui/icons-material/Verified';
import CodeMirror from '@uiw/react-codemirror';
import { EditorView } from '@codemirror/view';
import { Problem, ProgrammingLanguage, Submission } from '../types';
import { devPracticeApi } from '../api/devPracticeApi';
import {
  IN_PROGRESS_STATUSES,
  LANGUAGE_LABEL,
  LANGUAGES,
  SUBMISSION_STATUS_COLOR,
  SUBMISSION_STATUS_LABEL,
} from '../constants';
import { LANGUAGE_EXTENSIONS } from '../utils/codeLanguages';
import { useNotification } from '@shared/contexts/NotificationContext';
import SubmitButton from '@shared/components/SubmitButton';

// Judge0 via RapidAPI usually finishes in a few seconds per test case; ~2 minutes covers a slow
// queue. Past that the submission isn't lost — it's still judging server-side, and reopening the
// page shows its final status in the history list.
const POLL_INTERVAL_MS = 1500;
const MAX_POLLS = 80;
const HISTORY_SIZE = 5;

const chrome = EditorView.theme({
  '&': { fontSize: '0.8rem' },
  '&.cm-focused': { outline: 'none' },
});

function isInProgress(s: Submission | null): boolean {
  return s !== null && IN_PROGRESS_STATUSES.includes(s.status);
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
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
  const theme = useTheme();
  const { showError } = useNotification();
  const [language, setLanguage] = useState<ProgrammingLanguage>('JAVA');
  const [codes, setCodes] = useState<Partial<Record<ProgrammingLanguage, string>>>({});
  // Starters fetched for the current contract version (cleared when it changes, which triggers a
  // re-fetch) vs. the starter each editor was last filled with (never cleared) — the latter is what
  // tells an untouched editor apart from typed code.
  const [starters, setStarters] = useState<Partial<Record<ProgrammingLanguage, string>>>({});
  const lastFilledStarter = useRef<Partial<Record<ProgrammingLanguage, string>>>({});
  const [current, setCurrent] = useState<Submission | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [pollGaveUp, setPollGaveUp] = useState(false);
  const [history, setHistory] = useState<Submission[]>([]);
  const [publishOnAccept, setPublishOnAccept] = useState(false);
  // Publish-on-accept only means something for a draft (the backend ignores it otherwise).
  const canPublishOnAccept = problem.status === 'DRAFT';

  const code = codes[language] ?? '';
  const extensions = useMemo(() => [chrome, LANGUAGE_EXTENSIONS[language]()], [language]);
  const verified = Boolean(problem.verified) && !contractDirty;

  const loadHistory = useCallback(() => {
    devPracticeApi.listReferenceSubmissions(problem.id, HISTORY_SIZE)
      .then(page => setHistory(page.content))
      .catch(() => { /* cosmetic — the panel works without it */ });
  }, [problem.id]);

  useEffect(loadHistory, [loadHistory]);

  // A new contract version means a new saved signature: drop every cached starter so the current
  // language's is re-fetched below.
  useEffect(() => {
    setStarters({});
  }, [problem.contractVersion]);

  useEffect(() => {
    if (starters[language] !== undefined) return;
    let cancelled = false;
    devPracticeApi.getStarterCode(problem.id, language)
      .then(({ code: starter }) => {
        if (cancelled) return;
        const previous = lastFilledStarter.current[language];
        lastFilledStarter.current[language] = starter;
        setStarters(prev => ({ ...prev, [language]: starter }));
        // Only fill an editor nobody has typed into: empty, or still showing the old starter.
        setCodes(prev => (prev[language] === undefined || prev[language] === previous
          ? { ...prev, [language]: starter }
          : prev));
      })
      .catch(() => { /* leave the editor empty; the admin can still type a full solution */ });
    return () => { cancelled = true; };
  }, [problem.id, language, starters]);

  // Poll the in-flight submission until the judge reaches a final status.
  useEffect(() => {
    if (!current || !isInProgress(current)) return;
    let cancelled = false;
    let polls = 0;
    let timer: ReturnType<typeof setTimeout>;
    const tick = async () => {
      polls += 1;
      try {
        const next = await devPracticeApi.getReferenceSubmission(problem.id, current.id);
        if (cancelled) return;
        if (isInProgress(next)) {
          if (polls >= MAX_POLLS) setPollGaveUp(true);
          else timer = setTimeout(tick, POLL_INTERVAL_MS);
          return;
        }
        setCurrent(next);
        loadHistory();
        if (next.status === 'ACCEPTED' && next.contractVersion === problem.contractVersion) {
          onVerified(Boolean(next.publishOnAccept));
        }
      } catch {
        if (!cancelled && polls < MAX_POLLS) timer = setTimeout(tick, POLL_INTERVAL_MS);
      }
    };
    timer = setTimeout(tick, POLL_INTERVAL_MS);
    return () => { cancelled = true; clearTimeout(timer); };
    // Re-arm only when a different submission starts, not on every status update of the same one.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current?.id]);

  const handleRun = async () => {
    setSubmitting(true);
    setPollGaveUp(false);
    try {
      setCurrent(await devPracticeApi.createReferenceSubmission(
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

      <Stack direction="row" justifyContent="flex-end" sx={{ mb: 1 }}>
        <ToggleButtonGroup
          size="small"
          exclusive
          value={language}
          onChange={(_, v: ProgrammingLanguage | null) => { if (v) setLanguage(v); }}
        >
          {LANGUAGES.map(l => <ToggleButton key={l.value} value={l.value}>{l.label}</ToggleButton>)}
        </ToggleButtonGroup>
      </Stack>

      <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
        <CodeMirror
          value={code}
          onChange={v => setCodes(prev => ({ ...prev, [language]: v }))}
          theme={theme.palette.mode === 'dark' ? 'dark' : 'light'}
          extensions={extensions}
          minHeight="200px"
          maxHeight="420px"
        />
      </Box>

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
                <Chip
                  label={SUBMISSION_STATUS_LABEL[s.status]}
                  color={SUBMISSION_STATUS_COLOR[s.status]}
                  size="small"
                  sx={{ minWidth: 110 }}
                />
                <Typography variant="body2" sx={{ minWidth: 80 }}>{LANGUAGE_LABEL[s.language]}</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
                  {s.contractVersion === null
                    ? 'not judged yet'
                    : s.contractVersion === problem.contractVersion
                      ? `v${s.contractVersion} (current)`
                      : `v${s.contractVersion} (outdated)`}
                  {' · '}{formatTime(s.submittedAt)}
                </Typography>
                <Button
                  size="small"
                  onClick={() => {
                    setLanguage(s.language);
                    setCodes(prev => ({ ...prev, [s.language]: s.sourceCode }));
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
    <Alert severity={accepted && !stale ? 'success' : accepted ? 'warning' : submission.status === 'JUDGE_ERROR' ? 'warning' : 'error'} sx={{ mt: 1.5 }}>
      <Typography variant="body2" fontWeight={700}>
        {SUBMISSION_STATUS_LABEL[submission.status]} — {passed}/{total} test cases passed
      </Typography>
      {accepted && !stale && (
        <Typography variant="body2">
          {!submission.publishOnAccept
            ? 'The problem is verified. You can now set its status to Published and save.'
            : problemStatus === 'PUBLISHED'
              ? 'The problem is verified and has been published.'
              : problemStatus === 'DRAFT'
                ? 'The problem is verified — publishing…'
                : 'The problem is verified, but it was not published: it is no longer a draft.'}
        </Typography>
      )}
      {accepted && stale && (
        <Typography variant="body2">This run was judged against an older version of the problem — run it again.</Typography>
      )}
      {submission.errorMessage && (
        <Box component="pre" sx={{ m: 0, mt: 1, whiteSpace: 'pre-wrap', fontFamily: 'monospace', fontSize: '0.75rem' }}>
          {submission.errorMessage}
        </Box>
      )}
    </Alert>
  );
}
