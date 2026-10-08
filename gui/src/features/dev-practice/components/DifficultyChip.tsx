import { Chip } from '@mui/material';
import { Difficulty } from '../types';
import { DIFFICULTY_COLOR, DIFFICULTY_LABEL } from '../constants';

/** A problem's difficulty as a small outlined chip — green Easy, amber Medium, red Hard. */
export default function DifficultyChip({ difficulty }: { difficulty: Difficulty }): JSX.Element {
  return (
    <Chip size="small" variant="outlined" label={DIFFICULTY_LABEL[difficulty]} color={DIFFICULTY_COLOR[difficulty]} />
  );
}
