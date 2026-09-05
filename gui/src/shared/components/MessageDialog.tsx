import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';

interface Props {
  open: boolean;
  title: string;
  message: string;
  actionLabel?: string;
  onAction: () => void;
  /** Omit to make this dialog dismissable only via the action button — used whenever the
   * underlying state genuinely has nothing else to do but acknowledge and move on (e.g. a payment
   * window that just expired). Pass a real handler for a dialog that's fine being dismissed
   * without taking the action too. */
  onClose?: () => void;
}

/**
 * A generic "here's what happened, here's the one thing to do next" dialog — the informational
 * counterpart to `ConfirmDialog` (which is for a yes/no decision, not a plain notice). Deliberately
 * shared, not feature-specific — reuse this for any flow that needs to interrupt the shopper with
 * a message and a single follow-up action, not just one call site.
 */
export default function MessageDialog({
  open,
  title,
  message,
  actionLabel = 'OK',
  onAction,
  onClose,
}: Props): JSX.Element {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{message}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onAction} variant="contained">
          {actionLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
