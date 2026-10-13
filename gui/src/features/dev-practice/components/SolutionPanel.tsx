import { Alert, Box, Button, Chip, Divider, Stack, Typography } from '@mui/material';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import { ProblemSolution } from '../types';
import { LANGUAGE_LABEL } from '../constants';
import { submissionStats } from '../utils/format';
import { ProblemSolutionsState } from '../hooks/useProblemSolutions';
import MarkdownView from '@shared/components/MarkdownView';
import SectionStatus from '@shared/components/SectionStatus';

interface SolutionPanelProps {
  state: ProblemSolutionsState;
  /**
   * Whether the editorial shows: the learner solved the problem or chose to reveal it. `null` while
   * their progress is still loading — undecided, so a solver never sees the gate flash first.
   */
  revealed: boolean | null;
  onReveal: () => void;
}

/**
 * The workspace's Solution tab: the problem's editorial approaches, in order. Behind a soft spoiler
 * gate until the learner has solved the problem — one click shows it anyway, so someone stuck is never
 * locked out. Each approach shows a header (title, its verified languages with runtime/memory) and its
 * explanation; the code itself is inside the explanation, already expanded server-side from the
 * approach's verified reference run (`{{code:LANGUAGE}}`), so it's never repeated in a second block.
 */
export default function SolutionPanel({ state, revealed, onReveal }: SolutionPanelProps): JSX.Element {
  if (revealed === null) {
    return <SectionStatus loading isEmpty={false} emptyMessage="" />;
  }
  if (!revealed) {
    return (
      <Box sx={{ textAlign: 'center', py: 6, px: 2, maxWidth: 420, mx: 'auto' }}>
        <LockOutlinedIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1.5 }} />
        <Typography variant="subtitle1" fontWeight={700}>Try it yourself first</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2.5 }}>
          Working through a problem is how it sticks. The solution is here whenever you need it.
        </Typography>
        <Button variant="outlined" startIcon={<VisibilityOutlinedIcon />} onClick={onReveal}>
          Show solution
        </Button>
      </Box>
    );
  }

  const { solutions, loading, failed, retry } = state;
  if (failed) {
    return (
      <Alert severity="error" action={<Button color="inherit" size="small" onClick={retry}>Retry</Button>}>
        Couldn&apos;t load the solution.
      </Alert>
    );
  }
  if (loading || solutions === null || solutions.length === 0) {
    return (
      <SectionStatus
        loading={loading || solutions === null}
        isEmpty={solutions !== null && solutions.length === 0}
        emptyMessage="There's no written solution for this problem yet."
      />
    );
  }

  return (
    <Stack spacing={3} divider={<Divider />}>
      {solutions.map((solution, index) => (
        <ApproachSection key={solution.id} solution={solution} number={solutions.length > 1 ? index + 1 : null} />
      ))}
    </Stack>
  );
}

/** One approach: "Approach 1: Sorting", a chip per verified language with its measurements, the explanation. */
function ApproachSection({ solution, number }: { solution: ProblemSolution; number: number | null }): JSX.Element {
  return (
    <Box>
      <Typography variant="h6" fontWeight={700} sx={{ mb: 0.75 }}>
        {number !== null && `Approach ${number}: `}{solution.title}
      </Typography>
      {solution.codes.length > 0 && (
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mb: 1.5 }}>
          {solution.codes.map(code => {
            const stats = submissionStats(code);
            return (
              <Chip
                key={code.language}
                size="small"
                variant="outlined"
                label={stats ? `${LANGUAGE_LABEL[code.language]} · ${stats}` : LANGUAGE_LABEL[code.language]}
              />
            );
          })}
        </Stack>
      )}
      <MarkdownView content={solution.explanation} />
    </Box>
  );
}
