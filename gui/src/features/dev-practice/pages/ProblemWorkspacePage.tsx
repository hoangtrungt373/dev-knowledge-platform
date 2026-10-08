import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Box, Button, IconButton, Paper, Stack, Tab, Tabs, Tooltip, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import SendIcon from '@mui/icons-material/Send';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import LoginIcon from '@mui/icons-material/Login';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import { Group, Panel, useDefaultLayout } from 'react-resizable-panels';
import { practiceApi } from '../api/practiceApi';
import { MAX_RUN_CASES } from '../constants';
import { isInProgress, useSubmissionPolling } from '../hooks/useSubmissionPolling';
import { useSolutionDrafts } from '../hooks/useSolutionDrafts';
import { useProblemProgress } from '../hooks/useProblemProgress';
import { usePublishedProblem } from '../hooks/usePublishedProblem';
import { useCodeRun } from '../hooks/useCodeRun';
import ProgressMarker from '../components/ProgressMarker';
import DifficultyChip from '../components/DifficultyChip';
import TagChips from '../components/TagChips';
import SolutionEditor from '../components/SolutionEditor';
import SampleTestCases from '../components/SampleTestCases';
import SubmissionHistory from '../components/SubmissionHistory';
import RunCaseEditor from '../components/RunCaseEditor';
import ConsoleResult from '../components/ConsoleResult';
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
type ConsoleTab = 'testcase' | 'result';

/**
 * The learner's workspace for one problem — `/practice/:slug`, public to read; submitting needs a
 * login. A resizable split (widths remembered per browser): description, sample cases and the
 * learner's own submissions on the left; the editor, a console (editable test cases for Run, and the
 * latest Run/Submit result) and the Run/Submit buttons on the right. Run tries the console's cases
 * without saving (synchronous, `useCodeRun`); Submit is graded on every test and judged
 * asynchronously (`useSubmissionPolling`, shared with the admin reference panel).
 *
 * Drafts are kept in `localStorage` per problem (and per language within it), so a reload or a
 * later visit picks up where the learner stopped.
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

  const { problem, loading, notFound } = usePublishedProblem(slug);
  const [leftTab, setLeftTab] = useState<LeftTab>('description');
  const [consoleTab, setConsoleTab] = useState<ConsoleTab>('testcase');
  // Which action the Result tab shows: the last one the learner started.
  const [lastAction, setLastAction] = useState<'run' | 'submit' | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [historyKey, setHistoryKey] = useState(0);
  const [confirmReset, setConfirmReset] = useState(false);

  const { defaultLayout, onLayoutChanged } = useDefaultLayout({
    id: 'practice-workspace-layout',
    storage: window.localStorage,
    panelIds: ['practice-problem', 'practice-editor'],
  });

  const drafts = useSolutionDrafts({
    loadStarter: language => practiceApi.getStarterCode(slug, language).then(s => s.code),
    starterVersion: slug,
    // One storage entry per problem, holding every language's draft and the last language used.
    storageKey: `practice-draft:${slug}`,
  });
  const codeRun = useCodeRun(problem);
  const progress = useProblemProgress();

  const { submission, gaveUp, track } = useSubmissionPolling(
    id => practiceApi.getSubmission(id),
    final => {
      setHistoryKey(k => k + 1);
      progress.markJudged(final.problemId, final.status === 'ACCEPTED');
    },
  );

  const judging = submitting || (isInProgress(submission) && !gaveUp);
  // Run and Submit share the editor's code and the console, so one blocks the other.
  const busy = judging || codeRun.running;
  const hasCode = drafts.code.trim() !== '';

  /** Both actions show their result in the console's Result tab. */
  const startAction = (action: 'run' | 'submit') => {
    setLastAction(action);
    setConsoleTab('result');
  };

  const handleRun = () => {
    startAction('run');
    void codeRun.run(drafts.language, drafts.code);
  };

  const handleSubmit = async () => {
    if (!problem) return;
    startAction('submit');
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
        <DifficultyChip difficulty={problem.difficulty} />
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
                    <TagChips tags={problem.tags} />
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

          {/* ── Right: editor, console, actions ── */}
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

              {/* ── Console: editable cases for Run, and the latest Run/Submit result ── */}
              <Box sx={{ height: '40%', minHeight: 180, display: 'flex', flexDirection: 'column', border: 1, borderColor: 'divider', borderRadius: 1 }}>
                <Tabs
                  value={consoleTab}
                  onChange={(_, v: ConsoleTab) => setConsoleTab(v)}
                  sx={{ minHeight: 36, borderBottom: 1, borderColor: 'divider', '& .MuiTab-root': { minHeight: 36, py: 0 } }}
                >
                  <Tab value="testcase" label="Testcase" />
                  <Tab value="result" label="Result" />
                </Tabs>
                <Box sx={{ flex: 1, minHeight: 0, overflow: 'auto', p: 1.5 }}>
                  {consoleTab === 'testcase' ? (
                    <RunCaseEditor
                      parameters={problem.parameters}
                      cases={codeRun.cases}
                      onChange={codeRun.setCases}
                      onReset={codeRun.resetCases}
                      maxCases={MAX_RUN_CASES}
                      disabled={busy}
                    />
                  ) : (
                    <ConsoleResult
                      lastAction={lastAction}
                      running={codeRun.running}
                      runOutcome={codeRun.outcome}
                      submission={submission}
                      gaveUp={gaveUp}
                      parameters={problem.parameters}
                    />
                  )}
                </Box>
              </Box>

              <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1} sx={{ flexShrink: 0 }}>
                <Typography variant="caption" color="text.secondary">
                  Run tries your cases without saving · Submit judges every test, hidden ones included.
                </Typography>
                {isAuthed ? (
                  <Stack direction="row" spacing={1}>
                    <Button
                      variant="outlined"
                      startIcon={<PlayArrowIcon />}
                      onClick={handleRun}
                      disabled={busy || !hasCode || codeRun.cases.length === 0}
                    >
                      {codeRun.running ? 'Running…' : 'Run'}
                    </Button>
                    <SubmitButton
                      saving={judging}
                      onClick={handleSubmit}
                      label="Submit"
                      startIcon={<SendIcon />}
                      disabled={codeRun.running || !hasCode}
                    />
                  </Stack>
                ) : (
                  // Draft code is in localStorage, so it is still here after logging in and coming back.
                  // Run needs a login too: it uses the same judge, which isn't open to anonymous traffic.
                  <Button variant="contained" startIcon={<LoginIcon />} onClick={() => navigate('/login')}>
                    Log in to run &amp; submit
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
