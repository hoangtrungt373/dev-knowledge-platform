import { Alert, Box, Button, Chip, Divider, Stack, Typography } from '@mui/material';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import { ProblemSolution, ProgrammingLanguage } from '../types';
import { LANGUAGE_LABEL, LANGUAGES } from '../constants';
import { submissionStats } from '../utils/format';
import { ProblemSolutionsState } from '../hooks/useProblemSolutions';
import LanguageToggle from './LanguageToggle';
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
  /** The language the explanations' code is shown in, where an approach has it. */
  language: ProgrammingLanguage;
  onLanguageChange: (language: ProgrammingLanguage) => void;
}

/**
 * The workspace's Solution tab: the problem's editorial approaches, in order. Behind a soft spoiler
 * gate until the learner has solved the problem — one click shows it anyway, so someone stuck is never
 * locked out. Each approach shows a header (title, its verified languages with runtime/memory) and its
 * explanation; the code itself is inside the explanation, already expanded server-side from the
 * approach's verified reference run (`{{code:LANGUAGE}}`), so it's never repeated in a second block.
 * A language toggle (shown when the editorial has code in more than one language) asks the server to
 * render that language instead; an approach without it says so and keeps the author's language.
 */
export default function SolutionPanel({ state, revealed, onReveal, language, onLanguageChange }: SolutionPanelProps): JSX.Element {
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
  const failure = failed && (
    <Alert severity="error" action={<Button color="inherit" size="small" onClick={retry}>Retry</Button>}>
      Couldn&apos;t load the solution{solutions ? ' in that language' : ''}.
    </Alert>
  );
  // Nothing loaded yet: the error is all there is to show.
  if (failed && solutions === null) {
    return <>{failure}</>;
  }
  // A language switch keeps the previous editorial on screen while it loads — only the first load spins.
  if (solutions === null || solutions.length === 0) {
    return (
      <SectionStatus
        loading={loading || solutions === null}
        isEmpty={solutions !== null && solutions.length === 0}
        emptyMessage="There's no written solution for this problem yet."
      />
    );
  }

  // Every language some approach has verified code in, in the usual order.
  const available = LANGUAGES.map(l => l.value)
    .filter(l => solutions.some(s => s.codes.some(c => c.language === l)));

  return (
    <Stack spacing={2}>
      {/* A failed language switch keeps the previous editorial below the error. */}
      {failure}
      {available.length > 1 && (
        <Stack direction="row" alignItems="center" justifyContent="flex-end" spacing={1}>
          <Typography variant="caption" color="text.secondary">Show code in</Typography>
          <LanguageToggle value={language} onChange={onLanguageChange} languages={available} disabled={loading} />
        </Stack>
      )}
      <Stack spacing={3} divider={<Divider />}>
        {solutions.map((solution, index) => (
          <ApproachSection
            key={solution.id}
            solution={solution}
            number={solutions.length > 1 ? index + 1 : null}
            language={language}
          />
        ))}
      </Stack>
    </Stack>
  );
}

/**
 * One approach: "Approach 1: Sorting", a chip per verified language with its measurements, a note when it
 * has no code in the chosen language (the explanation then keeps the author's language), the explanation.
 */
function ApproachSection({ solution, number, language }: {
  solution: ProblemSolution;
  number: number | null;
  language: ProgrammingLanguage;
}): JSX.Element {
  const hasChosenLanguage = solution.codes.some(c => c.language === language);
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
      {solution.codes.length > 0 && !hasChosenLanguage && (
        <Typography variant="caption" color="text.secondary" component="p" sx={{ mb: 1 }}>
          No {LANGUAGE_LABEL[language]} version of this approach yet — showing {
            solution.codes.map(c => LANGUAGE_LABEL[c.language]).join(' / ')
          } instead.
        </Typography>
      )}
      <MarkdownView content={solution.explanation} />
    </Box>
  );
}
