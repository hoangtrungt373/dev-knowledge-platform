import { useEffect, useState } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField } from '@mui/material';
import { ProblemTag } from '../types';
import { devPracticeApi } from '../api/devPracticeApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import SubmitButton from '@shared/components/SubmitButton';

interface Props {
  open: boolean;
  /** `null` = create mode. */
  tag: ProblemTag | null;
  onClose: () => void;
  onSaved: () => void;
}

/** Create/rename one problem tag — mirrors @ecommerce's ProductTagFormDialog (name only; the slug
 * is regenerated server-side). Short form, so no close (✕) icon, per this app's dialog convention. */
export default function ProblemTagFormDialog({ open, tag, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState('');
  const { loading: saving, guard } = useSubmitGuard();
  const isEdit = tag !== null;

  useEffect(() => {
    if (open) {
      setName(tag?.name ?? '');
      setNameError('');
    }
  }, [open, tag]);

  const handleSubmit = (): void => {
    const trimmed = name.trim();
    if (!trimmed) {
      setNameError('Name is required');
      return;
    }
    guard(async () => {
      try {
        if (isEdit) {
          await devPracticeApi.updateProblemTag(tag.id, trimmed, showError);
          showSuccess('Tag updated');
        } else {
          await devPracticeApi.createProblemTag(trimmed, showError);
          showSuccess('Tag created');
        }
        onSaved();
        onClose();
      } catch {
        // showError already called (e.g. PROBLEM_TAG_NAME_CONFLICT)
      }
    });
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{isEdit ? 'Edit Tag' : 'New Tag'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="Name"
            value={name}
            onChange={e => { setName(e.target.value); setNameError(''); }}
            onKeyDown={e => { if (e.key === 'Enter') handleSubmit(); }}
            error={Boolean(nameError)}
            helperText={nameError || (isEdit
              ? 'Renaming updates the tag on every problem that uses it'
              : 'e.g. Array, Two Pointers, Dynamic Programming')}
            fullWidth
            autoFocus
            inputProps={{ maxLength: 100 }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Cancel</Button>
        <SubmitButton saving={saving} onClick={handleSubmit} label={isEdit ? 'Save' : 'Create'} />
      </DialogActions>
    </Dialog>
  );
}
