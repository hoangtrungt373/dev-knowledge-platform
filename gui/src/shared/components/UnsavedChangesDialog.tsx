import { Blocker } from 'react-router-dom';
import ConfirmDialog from './ConfirmDialog';

interface Props {
  /** From `useUnsavedChangesGuard`. */
  blocker: Blocker;
  message?: string;
}

/**
 * The in-app half of `useUnsavedChangesGuard`: shown while a navigation is blocked. "Leave"
 * continues it (the changes are lost), "Cancel" stays on the page with every edit intact.
 */
export default function UnsavedChangesDialog({
  blocker,
  message = 'You have unsaved changes. If you leave this page, they will be lost.',
}: Props): JSX.Element {
  return (
    <ConfirmDialog
      open={blocker.state === 'blocked'}
      title="Leave without saving?"
      message={message}
      confirmLabel="Leave"
      onConfirm={() => blocker.proceed?.()}
      onCancel={() => blocker.reset?.()}
    />
  );
}
