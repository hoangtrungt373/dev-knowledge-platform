import { useEffect, useState } from 'react';
import { Box, Button, Chip, Divider, Stack, Typography } from '@mui/material';
import { ProgrammingLanguage, Submission } from '../types';
import { practiceApi } from '../api/practiceApi';
import { LANGUAGE_LABEL, SUBMISSION_STATUS_COLOR, SUBMISSION_STATUS_LABEL } from '../constants';
import SectionStatus from '@shared/components/SectionStatus';

const HISTORY_SIZE = 50;

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

interface Props {
  problemId: number;
  /** Bump to refetch — the workspace increments it whenever one of its submissions finishes. */
  refreshKey: number;
  /** Puts a past submission's code back into the editor. */
  onLoadCode: (language: ProgrammingLanguage, code: string) => void;
}

/**
 * The learner's own submissions for one problem, newest first. Only the caller's `USER` submissions
 * ever come back (the backend scopes by the JWT), so there is nothing to filter here.
 */
export default function SubmissionHistory({ problemId, refreshKey, onLoadCode }: Props): JSX.Element {
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    // Only the first load shows a spinner; a refresh after a new verdict swaps the list in place.
    practiceApi.listSubmissions(problemId, HISTORY_SIZE)
      .then(page => { if (!cancelled) setSubmissions(page.content); })
      .catch(() => { /* leave the list as it was */ })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [problemId, refreshKey]);

  if (loading || submissions.length === 0) {
    return (
      <SectionStatus
        loading={loading}
        isEmpty={submissions.length === 0}
        emptyMessage="No submissions yet — your attempts will show up here."
      />
    );
  }

  return (
    <Stack divider={<Divider />}>
      {submissions.map(s => (
        <Stack key={s.id} direction="row" alignItems="center" spacing={1.5} sx={{ py: 1.25 }}>
          <Chip
            size="small"
            label={SUBMISSION_STATUS_LABEL[s.status]}
            color={SUBMISSION_STATUS_COLOR[s.status]}
            sx={{ minWidth: 120 }}
          />
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="body2">
              {LANGUAGE_LABEL[s.language]}
              {s.totalTestCases != null && s.passedTestCases != null
                && ` · ${s.passedTestCases}/${s.totalTestCases} passed`}
            </Typography>
            <Typography variant="caption" color="text.secondary">{formatTime(s.submittedAt)}</Typography>
          </Box>
          <Button size="small" onClick={() => onLoadCode(s.language, s.sourceCode)}>Load code</Button>
        </Stack>
      ))}
    </Stack>
  );
}
