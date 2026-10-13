import { TableCell, TableRow, Typography } from '@mui/material';
import { ProblemProgressStatus, ProblemSummary } from '../types';
import { MAX_TAG_CHIPS } from '../constants';
import ProgressMarker from './ProgressMarker';
import DifficultyChip from './DifficultyChip';
import TagChips from './TagChips';

interface ProblemTableRowProps {
  problem: ProblemSummary;
  status: ProblemProgressStatus | undefined;
  onOpen: () => void;
}

/**
 * One learner-facing problem row — marker, title, difficulty, topics — shared by the catalog list and
 * the study-plan page so a problem looks the same in both. Pair it with {@link PROBLEM_TABLE_COLUMNS}.
 */
export default function ProblemTableRow({ problem, status, onOpen }: ProblemTableRowProps): JSX.Element {
  return (
    <TableRow hover onClick={onOpen} sx={{ cursor: 'pointer' }}>
      <TableCell sx={{ pr: 0 }}>
        <ProgressMarker status={status} />
      </TableCell>
      <TableCell>
        <Typography variant="body2" fontWeight={600}>{problem.title}</Typography>
      </TableCell>
      <TableCell>
        <DifficultyChip difficulty={problem.difficulty} />
      </TableCell>
      <TableCell>
        <TagChips tags={problem.tags} max={MAX_TAG_CHIPS} />
      </TableCell>
    </TableRow>
  );
}

/** Header cells matching {@link ProblemTableRow}'s columns (status, title, difficulty, topics). */
export function ProblemTableHeaderCells(): JSX.Element {
  return (
    <>
      {/* Solved / attempted marker — empty for untouched problems and for logged-out visitors. */}
      <TableCell sx={{ width: 48 }} aria-label="Status" />
      <TableCell sx={{ fontWeight: 700 }}>Title</TableCell>
      <TableCell sx={{ fontWeight: 700, width: 120 }}>Difficulty</TableCell>
      <TableCell sx={{ fontWeight: 700 }}>Topics</TableCell>
    </>
  );
}

/** Column count of {@link ProblemTableRow} — for a full-width status/empty row. */
export const PROBLEM_TABLE_COLUMNS = 4;
