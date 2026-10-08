import { Chip } from '@mui/material';
import { SubmissionStatus } from '../types';
import { SUBMISSION_STATUS_COLOR, SUBMISSION_STATUS_LABEL } from '../constants';

/**
 * A submission's status as a filled chip. Fixed minimum width so the chips line up in a history
 * list, whatever the label length ("Accepted" vs "Time limit exceeded").
 */
export default function SubmissionStatusChip({ status }: { status: SubmissionStatus }): JSX.Element {
  return (
    <Chip
      size="small"
      label={SUBMISSION_STATUS_LABEL[status]}
      color={SUBMISSION_STATUS_COLOR[status]}
      sx={{ minWidth: 120 }}
    />
  );
}
