import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Button, Card, CardActionArea, CardActions, CardContent, Stack, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined';
import { StudyPlanSummary } from '../types';
import { practiceApi } from '../api/practiceApi';
import { useProblemProgress } from '../hooks/useProblemProgress';
import { countSolved } from '../utils/planProgress';
import PlanProgress from '../components/PlanProgress';
import PlanContinueButton from '../components/PlanContinueButton';
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
              {/* Fixed min height: the button renders nothing until progress loads, and the grid
                  shouldn't jump when it appears. */}
              <CardActions sx={{ px: 2, pb: 2, pt: 0, minHeight: 46 }}>
                <PlanContinueButton
                  planSlug={plan.slug}
                  planName={plan.name}
                  problems={plan.problems}
                  progress={progress}
                />
              </CardActions>
            </Card>
          ))}
        </Box>
      )}
    </Box>
  );
}
