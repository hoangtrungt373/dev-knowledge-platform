import { useEffect, useState } from 'react';
import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from '@mui/material';
import { Tag, TagStatus } from '../types';
import { contentApi } from '../api/contentApi';
import { useNotification } from '@shared/contexts/NotificationContext';

interface Props {
  open: boolean;
  tag: Tag | null;
  onClose: () => void;
  onSaved: () => void;
}

export default function TagFormDialog({ open, tag, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [name, setName] = useState('');
  const [status, setStatus] = useState<TagStatus>('ACTIVE');
  const [nameError, setNameError] = useState('');
  const [saving, setSaving] = useState(false);

  const isEdit = tag !== null;

  useEffect(() => {
    if (open) {
      setName(tag?.name ?? '');
      setStatus(tag?.status ?? 'ACTIVE');
      setNameError('');
    }
  }, [open, tag]);

  const validate = (): boolean => {
    if (!name.trim()) {
      setNameError('Name is required');
      return false;
    }
    if (name.trim().length > 100) {
      setNameError('Name must not exceed 100 characters');
      return false;
    }
    setNameError('');
    return true;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSaving(true);
    try {
      if (isEdit) {
        await contentApi.updateTag(tag.id, { name: name.trim(), status }, showError);
        showSuccess('Tag updated');
      } else {
        await contentApi.createTag({ name: name.trim(), status }, showError);
        showSuccess('Tag created');
      }
      onSaved();
      onClose();
    } catch {
      // showError already called by adminApi
    } finally {
      setSaving(false);
    }
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
            error={!!nameError}
            helperText={nameError || 'Slug is auto-generated from name'}
            fullWidth
            autoFocus
            inputProps={{ maxLength: 100 }}
          />

          <FormControl fullWidth>
            <InputLabel>Status</InputLabel>
            <Select
              label="Status"
              value={status}
              size="small"
              onChange={e => setStatus(e.target.value as TagStatus)}
            >
              <MenuItem value="ACTIVE">Active</MenuItem>
              <MenuItem value="INACTIVE">Inactive</MenuItem>
            </Select>
          </FormControl>
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Cancel</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={saving}>
          {saving ? <CircularProgress size={16} color="inherit" /> : (isEdit ? 'Save' : 'Create')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
