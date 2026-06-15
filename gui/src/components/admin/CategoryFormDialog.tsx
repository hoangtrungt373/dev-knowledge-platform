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
import { Category, CategoryTreeNode, CreateCategoryPayload, UpdateCategoryPayload } from '../../types/admin.types';
import { adminApi } from '../../api/adminApi';
import { useNotification } from '../../contexts/NotificationContext';

interface Props {
  open: boolean;
  category: Category | null;
  treeNodes: CategoryTreeNode[];
  onClose: () => void;
  onSaved: () => void;
}

interface FlatOption {
  id: number;
  name: string;
  depth: number;
}

function flattenTree(nodes: CategoryTreeNode[], depth = 0, excludeIds: Set<number> = new Set()): FlatOption[] {
  const result: FlatOption[] = [];
  for (const node of nodes) {
    if (excludeIds.has(node.id)) continue;
    result.push({ id: node.id, name: node.name, depth });
    result.push(...flattenTree(node.children, depth + 1, excludeIds));
  }
  return result;
}

function collectSubtreeIds(node: CategoryTreeNode, result: Set<number>): void {
  result.add(node.id);
  node.children.forEach(c => collectSubtreeIds(c, result));
}

function getSubtreeIds(tree: CategoryTreeNode[], categoryId: number): Set<number> {
  const result = new Set<number>();
  function walk(nodes: CategoryTreeNode[]) {
    for (const n of nodes) {
      if (n.id === categoryId) { collectSubtreeIds(n, result); return; }
      walk(n.children);
    }
  }
  walk(tree);
  return result;
}

export default function CategoryFormDialog({ open, category, treeNodes, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState<number | ''>('');
  const [nameError, setNameError] = useState('');
  const [saving, setSaving] = useState(false);

  const isEdit = category !== null;

  useEffect(() => {
    if (open) {
      setName(category?.name ?? '');
      setParentId(category?.parentId ?? '');
      setNameError('');
    }
  }, [open, category]);

  const excludeIds = isEdit ? getSubtreeIds(treeNodes, category.id) : new Set<number>();
  const flatOptions = flattenTree(treeNodes, 0, excludeIds);

  const validate = (): boolean => {
    if (!name.trim()) { setNameError('Name is required'); return false; }
    if (name.trim().length > 100) { setNameError('Name must not exceed 100 characters'); return false; }
    setNameError('');
    return true;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSaving(true);
    try {
      const resolvedParentId = parentId === '' ? null : parentId;
      if (isEdit) {
        const payload: UpdateCategoryPayload = { name: name.trim(), parentId: resolvedParentId };
        await adminApi.updateCategory(category.id, payload, showError);
        showSuccess('Category updated');
      } else {
        const payload: CreateCategoryPayload = { name: name.trim(), parentId: resolvedParentId ?? undefined };
        await adminApi.createCategory(payload, showError);
        showSuccess('Category created');
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
      <DialogTitle>{isEdit ? 'Edit Category' : 'New Category'}</DialogTitle>

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
