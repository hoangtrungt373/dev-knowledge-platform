import { useEffect, useState } from 'react';
import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material';
import { ProductCategory } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';

interface Props {
  open: boolean;
  category: ProductCategory | null;
  onClose: () => void;
  onSaved: () => void;
}

export default function ProductCategoryFormDialog({ open, category, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState('');
  const [saving, setSaving] = useState(false);

  const isEdit = category !== null;

  useEffect(() => {
    if (open) {
      setName(category?.name ?? '');
      setNameError('');
    }
  }, [open, category]);

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
        await ecommerceApi.updateProductCategory(category.id, { name: name.trim() }, showError);
        showSuccess('Product category updated');
      } else {
        await ecommerceApi.createProductCategory({ name: name.trim() }, showError);
        showSuccess('Product category created');
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
      <DialogTitle>{isEdit ? 'Edit Product Category' : 'New Product Category'}</DialogTitle>

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
