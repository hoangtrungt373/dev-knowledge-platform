import { useEffect, useState } from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material';
import { CreateProjectPayload, Project, UpdateProjectPayload } from '../types';
import { taskApi } from '../api/taskApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import SubmitButton from '@shared/components/SubmitButton';

interface Props {
  open: boolean;
  project: Project | null;
  onClose: () => void;
  onSaved: () => void;
}

export default function ProjectFormDialog({ open, project, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [nameError, setNameError] = useState('');
  const [saving, setSaving] = useState(false);

  const isEdit = project !== null;

  useEffect(() => {
    if (open) {
      setName(project?.name ?? '');
      setDescription(project?.description ?? '');
      setNameError('');
    }
  }, [open, project]);

  const validate = (): boolean => {
    if (!name.trim()) { setNameError('Name is required'); return false; }
    setNameError('');
    return true;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSaving(true);
    try {
      if (isEdit) {
        const payload: UpdateProjectPayload = { name: name.trim(), description: description.trim() || null };
        await taskApi.updateProject(project.id, payload, showError);
        showSuccess('Project updated');
      } else {
        const payload: CreateProjectPayload = { name: name.trim(), description: description.trim() || undefined };
        await taskApi.createProject(payload, showError);
        showSuccess('Project created');
      }
      onSaved();
      onClose();
    } catch {
      // showError already called
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{isEdit ? 'Edit Project' : 'New Project'}</DialogTitle>

      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="Name"
            value={name}
            onChange={e => { setName(e.target.value); setNameError(''); }}
            error={!!nameError}
            helperText={nameError}
            fullWidth
            autoFocus
          />
          <TextField
            label="Description"
            value={description}
            onChange={e => setDescription(e.target.value)}
            fullWidth
            multiline
            minRows={2}
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
