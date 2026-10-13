import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Button, Card, CardActionArea, CardActions, CardContent, Stack, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { PracticeReturnTo, StudyPlanSummary } from '../types';
import { practiceApi } from '../api/practiceApi';
import { ProblemProgressMap, useProblemProgress } from '../hooks/useProblemProgress';
import { countSolved, firstUnsolved } from '../utils/planProgress';
import PlanProgress from '../components/PlanProgress';
import { authService } from '@auth/services/authService';
import { useNotification } from '@shared/contexts/NotificationContext';
import FullPageLoader from '@shared/components/FullPageLoader';
import EmptyState from '@shared/components/EmptyState';

/**
 * `/practice/plans` — every study plan as a card with its size and, when logged in, "x / y solved"
 * (the plan's `problems` checked against the learner's own progress — no plan-specific progress
 * endpoint). Public, like the catalog. The card body opens the plan's page; its Start/Continue
 * button jumps straight into the first unsolved problem.
 */
export default function StudyPlanListPage(): JSX.Element {
  const navigate = useNavigate();
  const { showError } = useNotification();
  const progress = useProblemProgress();
  const isAuthed = authService.isAuthenticated();
  const [plans, setPlans] = useState<StudyPlanSummary[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    practiceApi.listStudyPlans(showError)
      .then(data => { if (!cancelled) setPlans(data); })
      .catch(() => { if (!cancelled) setPlans([]); });
    return () => { cancelled = true; };
  }, [showError]);

  if (plans === null) return <FullPageLoader />;

  return (
    <Box sx={{ p: 3, maxWidth: 1100, mx: 'auto' }}>
      <Button startIcon={<ArrowBackIcon />} size="small" onClick={() => navigate('/practice')} sx={{ mb: 1 }}>
        All problems
      </Button>
      <Box sx={{ mb: 2.5 }}>
        <Typography variant="h5" fontWeight={700}>Study plans</Typography>
        <Typography variant="body2" color="text.secondary">
          Curated problem lists, topic by topic, in the order worth solving them.
        </Typography>
      </Box>

      {plans.length === 0 ? (
        <EmptyState
          icon={<MenuBookOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled' }} />}
          title="No study plans yet"
          action={{ label: 'Browse problems', onClick: () => navigate('/practice') }}
        />
      ) : (
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
          {plans.map(plan => (
            <Card key={plan.id} variant="outlined" sx={{ display: 'flex', flexDirection: 'column' }}>
              {/* Body and button are siblings: a button nested inside CardActionArea (itself a button)
                  is invalid HTML and would fire both click handlers. */}
              <CardActionArea onClick={() => navigate(`/practice/plans/${plan.slug}`)} sx={{ flex: 1 }}>
                <CardContent>
                  <Stack spacing={1}>
                    <Typography variant="h6" fontWeight={700}>{plan.name}</Typography>
                    {plan.description && (
                      <Typography variant="body2" color="text.secondary">{plan.description}</Typography>
                    )}
                    <Typography variant="caption" color="text.secondary">
                      {plan.sectionCount} section{plan.sectionCount !== 1 ? 's' : ''}
                    </Typography>
                    <PlanProgress
                      solved={countSolved(plan.problems, progress.statusOf)}
                      total={plan.problems.length}
                      showProgress={isAuthed}
                    />
                  </Stack>
                </CardContent>
              </CardActionArea>
              <PlanCardAction plan={plan} progress={progress} />
            </Card>
          ))}
        </Box>
      )}
    </Box>
  );
}

interface PlanCardActionProps {
  plan: StudyPlanSummary;
  progress: ProblemProgressMap;
}

/**
 * The card's call to action: "Start" (no problem in the plan touched yet — always the case logged
 * out), "Continue" (any problem solved or attempted), or a "Completed" note once every problem is solved. Opens the first unsolved problem
 * in plan order — attempted-but-unsolved counts — with the plan as the workspace's back target.
 * Empty until progress has loaded, so a logged-in learner never sees "Start" flip to "Continue"; the
 * row keeps its height either way, so the grid doesn't jump.
 */
function PlanCardAction({ plan, progress }: PlanCardActionProps): JSX.Element {
  const navigate = useNavigate();
  const next = firstUnsolved(plan.problems, progress.statusOf);
  const started = plan.problems.some(p => progress.statusOf(p.id) !== undefined);
  const returnTo: PracticeReturnTo = { path: `/practice/plans/${plan.slug}`, label: plan.name };

  let content: JSX.Element | null = null;
  if (plan.problems.length > 0 && progress.loaded) {
    content = next ? (
      <Button
        size="small"
        variant={started ? 'contained' : 'outlined'}
        startIcon={<PlayArrowIcon />}
        onClick={() => navigate(`/practice/${next.slug}`, { state: { returnTo } })}
      >
        {started ? 'Continue' : 'Start'}
      </Button>
    ) : (
      <Stack direction="row" alignItems="center" spacing={0.5} sx={{ color: 'success.main' }}>
        <CheckCircleIcon fontSize="small" />
        <Typography variant="body2" fontWeight={600}>Completed</Typography>
      </Stack>
    );
  }

  return <CardActions sx={{ px: 2, pb: 2, pt: 0, minHeight: 46 }}>{content}</CardActions>;
}
