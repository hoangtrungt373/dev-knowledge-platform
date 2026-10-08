import { Tooltip } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import TimelapseIcon from '@mui/icons-material/Timelapse';
import { ProblemProgressStatus } from '../types';

/** A small solved (green check) / attempted (amber) icon; renders nothing for an untouched problem. */
export default function ProgressMarker({ status }: { status: ProblemProgressStatus | undefined }): JSX.Element | null {
  if (status === 'SOLVED') {
    return (
      <Tooltip title="Solved">
        <CheckCircleIcon fontSize="small" color="success" aria-label="Solved" />
      </Tooltip>
    );
  }
  if (status === 'ATTEMPTED') {
    return (
      <Tooltip title="Attempted — not solved yet">
        <TimelapseIcon fontSize="small" color="warning" aria-label="Attempted" />
      </Tooltip>
    );
  }
  return null;
}
