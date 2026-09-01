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
import { ProductCategory, ProductCategoryTreeNode, CreateProductCategoryPayload, UpdateProductCategoryPayload } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import { flattenCategoryTree } from '../utils/categoryTree';

interface Props {
  open: boolean;
  category: ProductCategory | null;
  treeNodes: ProductCategoryTreeNode[];
  onClose: () => void;
  onSaved: () => void;
}

function collectSubtreeIds(node: ProductCategoryTreeNode, result: Set<number>): void {
  result.add(node.id);
  node.children.forEach(c => collectSubtreeIds(c, result));
}

function getSubtreeIds(tree: ProductCategoryTreeNode[], categoryId: number): Set<number> {
  const result = new Set<number>();
  function walk(nodes: ProductCategoryTreeNode[]) {
    for (const n of nodes) {
      if (n.id === categoryId) { collectSubtreeIds(n, result); return; }
      walk(n.children);
    }
  }
  walk(tree);
  return result;
}

export default function ProductCategoryFormDialog({ open, category, treeNodes, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState<number | ''>('');
  const [nameError, setNameError] = useState('');
  const { loading: saving, guard } = useSubmitGuard();

  const isEdit = category !== null;

  useEffect(() => {
    if (open) {
      setName(category?.name ?? '');
      setParentId(category?.parentId ?? '');
      setNameError('');
    }
  }, [open, category]);

  // A category can't become its own descendant's child — exclude its own subtree from the picker.
  const excludeIds = isEdit ? getSubtreeIds(treeNodes, category.id) : new Set<number>();
  const flatOptions = flattenCategoryTree(treeNodes, 0, excludeIds);

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

  const handleSubmit = (): void => {
    if (!validate()) return;
    guard(async () => {
      try {
        const resolvedParentId = parentId === '' ? null : parentId;
        if (isEdit) {
          const payload: UpdateProductCategoryPayload = { name: name.trim(), parentId: resolvedParentId };
          await ecommerceApi.updateProductCategory(category.id, payload, showError);
          showSuccess('Product category updated');
        } else {
          const payload: CreateProductCategoryPayload = { name: name.trim(), parentId: resolvedParentId ?? undefined };
          await ecommerceApi.createProductCategory(payload, showError);
          showSuccess('Product category created');
        }
        onSaved();
        onClose();
      } catch {
        // showError already called
      }
    });
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

          <FormControl fullWidth size="small">
            <InputLabel>Parent category</InputLabel>
            <Select
              label="Parent category"
              value={parentId}
              onChange={e => setParentId(e.target.value as number | '')}
            >
              <MenuItem value=""><em>None (root category)</em></MenuItem>
              {flatOptions.map(opt => (
                <MenuItem key={opt.id} value={opt.id}>
                  <span style={{ paddingLeft: opt.depth * 16 }}>{opt.name}</span>
                </MenuItem>
              ))}
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
