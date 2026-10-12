import { Box, Stack, Typography } from '@mui/material';
import { Submission } from '../types';
import { LANGUAGE_LABEL } from '../constants';
import { formatMemory, formatRuntime } from '../utils/format';

/** One "Runtime 42 ms · Beats 87.3%" line; the "Beats" half only when a percentage exists. */
function StatLine({ label, value, beats }: { label: string; value: string; beats: number | null }): JSX.Element {
  return (
    <Typography variant="body2">
      {label}{' '}
      <Box component="span" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{value}</Box>
      {beats !== null && (
        <>
          {' · Beats '}
          <Box component="span" sx={{ fontWeight: 700 }}>{beats}%</Box>
        </>
      )}
    </Typography>
  );
}

/**
 * An accepted submission's runtime and memory, each with how many other learners it beats — the
 * LeetCode-style "Beats 87.3%" (from `GET /api/v1/submissions/{id}`, computed on read). The ranking is
 * against other users' best accepted solutions in the same language, so it says which language;
 * when nobody else has solved it in that language yet there's no percentage, and it says so instead.
 * Renders nothing for a submission without measurements.
 */
export default function PerformanceStats({ submission }: { submission: Submission }): JSX.Element | null {
  const { runtimeMs, memoryKb, runtimeBeatsPercent, memoryBeatsPercent, rankedAgainst, language } = submission;
  if (runtimeMs == null && memoryKb == null) return null;
  const languageLabel = LANGUAGE_LABEL[language];

  return (
    <Stack spacing={0.25} sx={{ mt: 0.75 }}>
      {runtimeMs != null && <StatLine label="Runtime" value={formatRuntime(runtimeMs)} beats={runtimeBeatsPercent} />}
      {memoryKb != null && <StatLine label="Memory" value={formatMemory(memoryKb)} beats={memoryBeatsPercent} />}
      {rankedAgainst === 0 && (
        <Typography variant="caption" color="text.secondary">
          The first accepted {languageLabel} solution — nobody to compare with yet.
        </Typography>
      )}
      {rankedAgainst != null && rankedAgainst > 0 && (
        <Typography variant="caption" color="text.secondary">
          Compared with the best {languageLabel} solution of {rankedAgainst} other learner{rankedAgainst !== 1 ? 's' : ''}.
          Times include the {languageLabel} runtime's own start-up.
        </Typography>
      )}
    </Stack>
  );
}
