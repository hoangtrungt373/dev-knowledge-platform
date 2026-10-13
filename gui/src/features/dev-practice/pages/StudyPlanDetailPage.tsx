import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import { PracticeReturnTo, StudyPlan, StudyPlanSection } from '../types';
import { practiceApi } from '../api/practiceApi';
import { ProblemProgressMap, useProblemProgress } from '../hooks/useProblemProgress';
import { countSolved } from '../utils/planProgress';
import PlanProgress from '../components/PlanProgress';
import ProblemTableRow, { PROBLEM_TABLE_COLUMNS, ProblemTableHeaderCells } from '../components/ProblemTableRow';
import { authService } from '@auth/services/authService';
import FullPageLoader from '@shared/components/FullPageLoader';
import EmptyState from '@shared/components/EmptyState';

/**
 * `/practice/plans/:slug` — one plan: its overall progress, then each section with its own progress
 * and its problems in order. A plan with a single section hides that section's header (a flat list).
 * Opening a problem passes `{ returnTo }` in router state, so the workspace's back button comes back
 * here instead of the catalog.
 */
export default function StudyPlanDetailPage(): JSX.Element {
  const { slug = '' } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const progress = useProblemProgress();
  const isAuthed = authService.isAuthenticated();
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
        <Box sx={{ minWidth: 200, alignSelf: { sm: 'center' } }}>
          <PlanProgress solved={countSolved(allProblems, progress.statusOf)} total={allProblems.length} showProgress={isAuthed} />
        </Box>
      </Stack>

      <Stack spacing={3}>
        {plan.sections.map(section => (
          <SectionBlock
            key={section.id}
            section={section}
            showHeader={!singleSection}
            progress={progress}
            showProgress={isAuthed}
            onOpen={openProblem}
          />
        ))}
      </Stack>
    </Box>
  );
}

interface SectionBlockProps {
  section: StudyPlanSection;
  showHeader: boolean;
  progress: ProblemProgressMap;
  showProgress: boolean;
  onOpen: (problemSlug: string) => void;
}

/** One section: title, description and progress, then its problems — the catalog's own row shape. */
function SectionBlock({ section, showHeader, progress, showProgress, onOpen }: SectionBlockProps): JSX.Element {
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
          <PlanProgress solved={countSolved(section.problems, progress.statusOf)} total={section.problems.length} showProgress={showProgress} />
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
            {section.problems.length === 0 ? (
              <TableRow>
                <TableCell colSpan={PROBLEM_TABLE_COLUMNS} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                  Problems for this section are coming soon.
                </TableCell>
              </TableRow>
            ) : section.problems.map(problem => (
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
