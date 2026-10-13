import { useNavigate } from 'react-router-dom';
import { Button, Stack, Typography } from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { PracticeReturnTo } from '../types';
import { ProblemProgressMap } from '../hooks/useProblemProgress';
import { firstUnsolved } from '../utils/planProgress';

interface PlanContinueButtonProps {
  planSlug: string;
  planName: string;
  /** The plan's problems in plan order — anything with an id and a slug (refs or full rows). */
  problems: { id: number; slug: string }[];
  progress: ProblemProgressMap;
  size?: 'small' | 'medium';
}

/**
 * A plan's call to action, shared by the overview cards and the plan page's header: "Start" (no
 * problem in the plan touched yet — always the case logged out), "Continue" (any problem solved or
 * attempted), or a "Completed" note once every problem is solved. Opens the first unsolved problem in
 * plan order — attempted-but-unsolved counts — with the plan as the workspace's back target.
 *
 * Renders nothing for a plan with no published problems, and nothing until progress has loaded, so a
 * logged-in learner never sees "Start" flip to "Continue" — callers that must not shift layout reserve
 * the space themselves.
 */
export default function PlanContinueButton(
  { planSlug, planName, problems, progress, size = 'small' }: PlanContinueButtonProps,
): JSX.Element | null {
  const navigate = useNavigate();
  if (problems.length === 0 || !progress.loaded) return null;

  const next = firstUnsolved(problems, progress.statusOf);
  if (!next) {
    return (
      <Stack direction="row" alignItems="center" spacing={0.5} sx={{ color: 'success.main' }}>
        <CheckCircleIcon fontSize="small" />
        <Typography variant="body2" fontWeight={600}>Completed</Typography>
      </Stack>
    );
  }

  const started = problems.some(p => progress.statusOf(p.id) !== undefined);
  const returnTo: PracticeReturnTo = { path: `/practice/plans/${planSlug}`, label: planName };
  return (
    <Button
      size={size}
      variant={started ? 'contained' : 'outlined'}
      startIcon={<PlayArrowIcon />}
      onClick={() => navigate(`/practice/${next.slug}`, { state: { returnTo } })}
    >
      {started ? 'Continue' : 'Start'}
    </Button>
  );
}
