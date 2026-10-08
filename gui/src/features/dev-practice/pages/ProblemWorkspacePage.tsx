import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  IconButton,
  Paper,
  Stack,
  Tab,
  Tabs,
  Tooltip,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import SendIcon from '@mui/icons-material/Send';
import LoginIcon from '@mui/icons-material/Login';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import { Group, Panel, useDefaultLayout } from 'react-resizable-panels';
import { Problem } from '../types';
import { practiceApi } from '../api/practiceApi';
import { DIFFICULTY_COLOR, DIFFICULTY_LABEL } from '../constants';
import { isInProgress, useSubmissionPolling } from '../hooks/useSubmissionPolling';
import { useSolutionDrafts } from '../hooks/useSolutionDrafts';
import { useProblemProgress } from '../hooks/useProblemProgress';
import ProgressMarker from '../components/ProgressMarker';
import SolutionEditor from '../components/SolutionEditor';
import SampleTestCases from '../components/SampleTestCases';
import SubmissionVerdict from '../components/SubmissionVerdict';
import SubmissionHistory from '../components/SubmissionHistory';
import { authService } from '@auth/services/authService';
import { useNotification } from '@shared/contexts/NotificationContext';
import FullPageLoader from '@shared/components/FullPageLoader';
import EmptyState from '@shared/components/EmptyState';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import MarkdownView from '@shared/components/MarkdownView';
import ResizeHandle from '@shared/components/ResizeHandle';
import SubmitButton from '@shared/components/SubmitButton';

const LIST_PATH = '/practice';
// The dense NavBar is 48px tall; the workspace fills the rest of the viewport.
const NAVBAR_HEIGHT_PX = 48;

type LeftTab = 'description' | 'submissions';

/**
 * The learner's workspace for one problem — `/practice/:slug`, public to read; submitting needs a
 * login. A resizable split (widths remembered per browser): description, sample cases and the
 * learner's own submissions on the left; the editor, verdict and Submit button on the right.
 *
 * Drafts are kept in `localStorage` per problem (and per language within it), so a reload or a
 * later visit picks up where the learner stopped. Judging is asynchronous, so a submission is
 * polled (`useSubmissionPolling`, shared with the admin reference panel) until the verdict lands.
 */
export default function ProblemWorkspacePage(): JSX.Element {
  const { slug = '' } = useParams<{ slug: string }>();
  // Keyed by slug: React Router keeps this component mounted when only :slug changes, and every
  // piece of workspace state (drafts, verdict, tab) belongs to one problem — a new key starts fresh.
  return <ProblemWorkspace key={slug} slug={slug} />;
}

function ProblemWorkspace({ slug }: { slug: string }): JSX.Element {
  const navigate = useNavigate();
  const { showError } = useNotification();
  const isAuthed = authService.isAuthenticated();

  const [problem, setProblem] = useState<Problem | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [leftTab, setLeftTab] = useState<LeftTab>('description');
  const [submitting, setSubmitting] = useState(false);
  const [historyKey, setHistoryKey] = useState(0);
  const [confirmReset, setConfirmReset] = useState(false);

  const { defaultLayout, onLayoutChanged } = useDefaultLayout({
    id: 'practice-workspace-layout',
    storage: window.localStorage,
    panelIds: ['practice-problem', 'practice-editor'],
  });

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    // No showError: a missing (or unpublished — the backend can't tell them apart on purpose) problem
    // gets its own "not found" page rather than a toast.
    practiceApi.getProblem(slug)
      .then(p => { if (!cancelled) setProblem(p); })
      .catch(() => { if (!cancelled) setNotFound(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [slug]);

  const drafts = useSolutionDrafts({
    loadStarter: language => practiceApi.getStarterCode(slug, language).then(s => s.code),
    starterVersion: slug,
    // One storage entry per problem, holding every language's draft and the last language used.
    storageKey: `practice-draft:${slug}`,
  });

  const progress = useProblemProgress();

  const { submission, gaveUp, track } = useSubmissionPolling(
    id => practiceApi.getSubmission(id),
    final => {
      setHistoryKey(k => k + 1);
      progress.markJudged(final.problemId, final.status === 'ACCEPTED');
    },
  );

  const judging = submitting || (isInProgress(submission) && !gaveUp);

  const handleSubmit = async () => {
    if (!problem) return;
    setSubmitting(true);
    try {
      track(await practiceApi.submit(problem.id, drafts.language, drafts.code, showError));
      setHistoryKey(k => k + 1); // the new PENDING row shows up in Submissions straight away
    } catch {
      // showError already called
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <FullPageLoader />;

  if (notFound || !problem) {
    return (
      <EmptyState
        icon={<SearchOffIcon sx={{ fontSize: 64, color: 'text.disabled' }} />}
        title="Problem not found"
        description="It may have been removed, or isn't published yet."
        action={{ label: 'Back to problems', onClick: () => navigate(LIST_PATH) }}
      />
    );
  }

  return (
    <Box
      sx={{
        height: `calc(100vh - ${NAVBAR_HEIGHT_PX}px)`,
        minHeight: 520,
        display: 'flex',
        flexDirection: 'column',
        p: 2,
        gap: 1.5,
      }}
    >
      <Stack direction="row" alignItems="center" spacing={1.5}>
        <Tooltip title="All problems">
          <IconButton size="small" onClick={() => navigate(LIST_PATH)}>
            <ArrowBackIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <Typography variant="h6" fontWeight={700} noWrap>{problem.title}</Typography>
        <Chip
          size="small"
          label={DIFFICULTY_LABEL[problem.difficulty]}
          color={DIFFICULTY_COLOR[problem.difficulty]}
          variant="outlined"
        />
        <ProgressMarker status={progress.statusOf(problem.id)} />
      </Stack>

      <Box sx={{ flex: 1, minHeight: 0 }}>
        <Group
          orientation="horizontal"
          defaultLayout={defaultLayout}
          onLayoutChanged={onLayoutChanged}
          style={{ height: '100%' }}
        >
          {/* ── Left: problem statement + own submissions ── */}
          <Panel id="practice-problem" defaultSize="45" minSize="25">
            <Paper variant="outlined" sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 1 }}>
                <Tabs value={leftTab} onChange={(_, v: LeftTab) => setLeftTab(v)}>
                  <Tab value="description" label="Description" />
                  <Tab value="submissions" label="Submissions" />
                </Tabs>
              </Box>
              <Box sx={{ flex: 1, minHeight: 0, overflow: 'auto', p: 2.5 }}>
                {leftTab === 'description' ? (
                  <Stack spacing={2.5}>
                    {problem.tags.length > 0 && (
                      <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                        {problem.tags.map(t => <Chip key={t.id} size="small" label={t.name} />)}
                      </Stack>
                    )}
                    <MarkdownView content={problem.description} />
                    <SampleTestCases parameters={problem.parameters} testCases={problem.testCases} />
                  </Stack>
                ) : isAuthed ? (
                  <SubmissionHistory
                    problemId={problem.id}
                    refreshKey={historyKey}
                    onLoadCode={(language, code) => {
                      drafts.setLanguage(language);
                      drafts.setCodeFor(language, code);
                    }}
                  />
                ) : (
                  <EmptyState
                    icon={<LoginIcon sx={{ fontSize: 48, color: 'text.disabled' }} />}
                    title="Log in to see your submissions"
                    action={{ label: 'Log in', onClick: () => navigate('/login') }}
                  />
                )}
              </Box>
            </Paper>
          </Panel>

          <ResizeHandle />

          {/* ── Right: editor, verdict, submit ── */}
          <Panel id="practice-editor" defaultSize="55" minSize="30">
            <Paper variant="outlined" sx={{ height: '100%', display: 'flex', flexDirection: 'column', p: 1.5, gap: 1.5 }}>
              <Box sx={{ flex: 1, minHeight: 0 }}>
                <SolutionEditor
                  sizing="fill"
                  language={drafts.language}
                  onLanguageChange={drafts.setLanguage}
                  code={drafts.code}
                  onCodeChange={drafts.setCode}
                  toolbar={(
                    <Tooltip title="Replace this language's code with the starter code">
                      <span>
                        <Button
                          size="small"
                          startIcon={<RestartAltIcon />}
                          disabled={!drafts.modified}
                          onClick={() => setConfirmReset(true)}
                        >
                          Reset
                        </Button>
                      </span>
                    </Tooltip>
                  )}
                />
              </Box>

              {submission && (
                <Box sx={{ maxHeight: '35%', overflow: 'auto', flexShrink: 0 }}>
                  <SubmissionVerdict submission={submission} gaveUp={gaveUp} />
                </Box>
              )}

              <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ flexShrink: 0 }}>
                <Typography variant="caption" color="text.secondary">
                  Your code is judged against every test case, including hidden ones.
                </Typography>
                {isAuthed ? (
                  <SubmitButton
                    saving={judging}
                    onClick={handleSubmit}
                    label="Submit"
                    startIcon={<SendIcon />}
                    disabled={!drafts.code.trim()}
                  />
                ) : (
                  // Draft code is in localStorage, so it is still here after logging in and coming back.
                  <Button variant="contained" startIcon={<LoginIcon />} onClick={() => navigate('/login')}>
                    Log in to submit
                  </Button>
                )}
              </Stack>
            </Paper>
          </Panel>
        </Group>
      </Box>

      <ConfirmDialog
        open={confirmReset}
        title="Reset to starter code?"
        message="Your code for this language will be replaced with the starter code. Other languages are kept."
        confirmLabel="Reset"
        onConfirm={() => { drafts.resetToStarter(); setConfirmReset(false); }}
        onCancel={() => setConfirmReset(false)}
      />
    </Box>
  );
}
