import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Chip,
  FormControlLabel,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import EmojiEventsOutlinedIcon from '@mui/icons-material/EmojiEventsOutlined';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { PracticeReturnTo, ProblemSummary, StudyPlan, StudyPlanSection } from '../types';
import { practiceApi } from '../api/practiceApi';
import { ProblemProgressMap, useProblemProgress } from '../hooks/useProblemProgress';
import { useHideSolved } from '../hooks/useHideSolved';
import { countSolved, firstUnsolved, recentlySolved } from '../utils/planProgress';
import { formatDateTime, formatRelativeTime } from '../utils/format';
import PlanProgress from '../components/PlanProgress';
import PlanContinueButton from '../components/PlanContinueButton';
import ProblemTableRow, { PROBLEM_TABLE_COLUMNS, ProblemTableHeaderCells } from '../components/ProblemTableRow';
import { authService } from '@auth/services/authService';
import FullPageLoader from '@shared/components/FullPageLoader';
import EmptyState from '@shared/components/EmptyState';

/**
 * `/practice/plans/:slug` — one plan: its overall progress, then each section with its own progress
 * and its problems in order. A plan with a single section hides that section's header (a flat list).
 * Opening a problem passes `{ returnTo }` in router state, so the workspace's back button comes back
 * here instead of the catalog. The header's Start/Continue button (shared with the overview cards)
 * jumps to the first unsolved problem.
 */
export default function StudyPlanDetailPage(): JSX.Element {
  const { slug = '' } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const progress = useProblemProgress();
  const isAuthed = authService.isAuthenticated();
  const [hideSolved, setHideSolved] = useHideSolved();
  const [plan, setPlan] = useState<StudyPlan | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    // No toast: an unknown slug gets the page's own "not found" state instead.
    practiceApi.getStudyPlan(slug)
      .then(p => { if (!cancelled) setPlan(p); })
      .catch(() => { if (!cancelled) setNotFound(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [slug]);

  if (loading) return <FullPageLoader />;

  if (notFound || !plan) {
    return (
      <EmptyState
        icon={<SearchOffIcon sx={{ fontSize: 64, color: 'text.disabled' }} />}
        title="Study plan not found"
        action={{ label: 'All study plans', onClick: () => navigate('/practice/plans') }}
      />
    );
  }

  const allProblems = plan.sections.flatMap(s => s.problems);
  const returnTo: PracticeReturnTo = { path: `/practice/plans/${plan.slug}`, label: plan.name };
  const openProblem = (problemSlug: string) => navigate(`/practice/${problemSlug}`, { state: { returnTo } });
  const singleSection = plan.sections.length === 1;
  const solvedCount = countSolved(allProblems, progress.statusOf);
  // Derived, never stored: publishing a new problem in the plan later simply makes it incomplete again.
  const completed = isAuthed && progress.loaded && allProblems.length > 0 && solvedCount === allProblems.length;

  return (
    <Box sx={{ p: 3, maxWidth: 1100, mx: 'auto' }}>
      <Button startIcon={<ArrowBackIcon />} size="small" onClick={() => navigate('/practice/plans')} sx={{ mb: 1 }}>
        All study plans
      </Button>
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={2} sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h5" fontWeight={700}>{plan.name}</Typography>
          {plan.description && (
            <Typography variant="body2" color="text.secondary">{plan.description}</Typography>
          )}
        </Box>
        <Stack
          direction="row"
          alignItems="center"
          spacing={2}
          sx={{ alignSelf: { sm: 'center' }, flexShrink: 0 }}
        >
          <Box sx={{ minWidth: 200 }}>
            <PlanProgress solved={solvedCount} total={allProblems.length} showProgress={isAuthed} />
          </Box>
          <PlanContinueButton
            planSlug={plan.slug}
            planName={plan.name}
            problems={allProblems}
            progress={progress}
            size="medium"
          />
        </Stack>
      </Stack>

      {isAuthed && progress.loaded && (
        <RecentlySolvedStrip problems={allProblems} progress={progress} onOpen={openProblem} />
      )}

      {completed && (
        <Alert
          severity="success"
          icon={<EmojiEventsOutlinedIcon />}
          action={(
            <Button color="inherit" size="small" onClick={() => navigate('/practice/plans')}>
              Browse plans
            </Button>
          )}
          sx={{ mb: 2 }}
        >
          <AlertTitle>Plan complete</AlertTitle>
          You've solved all {allProblems.length} problem{allProblems.length !== 1 ? 's' : ''} in {plan.name}.
        </Alert>
      )}

      {/* Logged out nothing is solved, so the toggle would do nothing — shown only once progress is in. */}
      {isAuthed && progress.loaded && allProblems.length > 0 && (
        <Stack direction="row" justifyContent="flex-end" sx={{ mb: 1 }}>
          <FormControlLabel
            control={<Switch size="small" checked={hideSolved} onChange={e => setHideSolved(e.target.checked)} />}
            label="Hide solved"
            slotProps={{ typography: { variant: 'body2' } }}
          />
        </Stack>
      )}

      <Stack spacing={3}>
        {plan.sections.map(section => (
          <SectionBlock
            key={section.id}
            section={section}
            showHeader={!singleSection}
            progress={progress}
            showProgress={isAuthed}
            hideSolved={isAuthed && hideSolved}
            onOpen={openProblem}
          />
        ))}
      </Stack>
    </Box>
  );
}

/** How many recently solved problems the plan page shows. */
const RECENTLY_SOLVED_LIMIT = 3;

interface RecentlySolvedStripProps {
  problems: ProblemSummary[];
  progress: ProblemProgressMap;
  onOpen: (problemSlug: string) => void;
}

/**
 * "Recently solved": the last few problems of this plan the learner solved, newest first, as chips
 * ("Contains Duplicate · 2 days ago", exact time on hover) that reopen the problem. Renders nothing
 * until something in the plan is solved. Times are each problem's first accepted submission
 * (`solvedAt` from the progress endpoint), so re-solving an old problem doesn't bump it.
 */
function RecentlySolvedStrip({ problems, progress, onOpen }: RecentlySolvedStripProps): JSX.Element | null {
  const recent = recentlySolved(problems, progress.solvedAtOf, RECENTLY_SOLVED_LIMIT);
  if (recent.length === 0) return null;
  return (
    <Stack direction="row" alignItems="center" spacing={1} useFlexGap flexWrap="wrap" sx={{ mb: 2 }}>
      <Typography variant="caption" color="text.secondary" fontWeight={700} sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}>
        Recently solved
      </Typography>
      {recent.map(({ problem, solvedAt }) => (
        <Tooltip key={problem.id} title={`Solved ${formatDateTime(solvedAt)}`}>
          <Chip
            size="small"
            variant="outlined"
            color="success"
            icon={<CheckCircleIcon />}
            label={`${problem.title} · ${formatRelativeTime(solvedAt)}`}
            onClick={() => onOpen(problem.slug)}
          />
        </Tooltip>
      ))}
    </Stack>
  );
}

interface SectionBlockProps {
  section: StudyPlanSection;
  showHeader: boolean;
  progress: ProblemProgressMap;
  showProgress: boolean;
  /** Leave solved rows out of the table; progress and the Next link still count the whole section. */
  hideSolved: boolean;
  onOpen: (problemSlug: string) => void;
}

/**
 * One section: title, description and progress, then its problems — the catalog's own row shape.
 * The header also links to the section's first unsolved problem ("Next: …"), for working on one topic
 * out of plan order; it's absent until progress loads, once the section is fully solved, and with the
 * header itself (a one-section plan, where the page's own Continue button already covers it).
 */
function SectionBlock({ section, showHeader, progress, showProgress, hideSolved, onOpen }: SectionBlockProps): JSX.Element {
  const next = progress.loaded ? firstUnsolved(section.problems, progress.statusOf) : undefined;
  const rows = hideSolved
    ? section.problems.filter(p => progress.statusOf(p.id) !== 'SOLVED')
    : section.problems;
  // "Coming soon" means the section has nothing published; "all solved" means every row was hidden.
  const emptyMessage = section.problems.length === 0
    ? 'Problems for this section are coming soon.'
    : `All ${section.problems.length} problem${section.problems.length !== 1 ? 's' : ''} in this section are solved.`;
  return (
    <Box>
      {showHeader && (
        <Stack direction="row" justifyContent="space-between" alignItems="flex-end" spacing={2} sx={{ mb: 1 }}>
          <Box>
            <Typography variant="h6" fontWeight={700}>{section.title}</Typography>
            {section.description && (
              <Typography variant="body2" color="text.secondary">{section.description}</Typography>
            )}
          </Box>
          <Stack alignItems="flex-end" spacing={0.5} sx={{ flexShrink: 0, maxWidth: '45%' }}>
            <PlanProgress solved={countSolved(section.problems, progress.statusOf)} total={section.problems.length} showProgress={showProgress} />
            {next && (
              <Button
                size="small"
                endIcon={<ArrowForwardIcon />}
                onClick={() => onOpen(next.slug)}
                sx={{ maxWidth: '100%', textTransform: 'none' }}
              >
                {/* Long titles truncate instead of pushing the header wider. */}
                <Box component="span" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  Next: {next.title}
                </Box>
              </Button>
            )}
          </Stack>
        </Stack>
      )}
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <ProblemTableHeaderCells />
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={PROBLEM_TABLE_COLUMNS} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                  {emptyMessage}
                </TableCell>
              </TableRow>
            ) : rows.map(problem => (
              <ProblemTableRow
                key={problem.id}
                problem={problem}
                status={progress.statusOf(problem.id)}
                onOpen={() => onOpen(problem.slug)}
              />
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
}
