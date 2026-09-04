import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import {
  ProductCategory, ProductCategoryTreeNode, CreateProductCategoryPayload, UpdateProductCategoryPayload,
  ProductAttribute, CategoryAttributeAssignmentInput,
} from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import SubmitButton from '@shared/components/SubmitButton';
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

/** Swaps `list[index]`/`list[index + direction]` — same convention `ImageThumbnailGrid.onMove`/
 * `ProductAttributeFormDialog`'s own value-reorder already use. */
function move<T>(list: T[], index: number, direction: -1 | 1): T[] {
  const next = [...list];
  const target = index + direction;
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

export default function ProductCategoryFormDialog({ open, category, treeNodes, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState<number | ''>('');
  const [nameError, setNameError] = useState('');
  const { loading: saving, guard } = useSubmitGuard();

  // The full attribute registry, fetched once per dialog open — this taxonomy is expected to stay
  // small (same assumption ProductCategoryApi's own unpaginated list already makes), so one
  // large-page fetch stands in for a real "list everything" endpoint rather than adding one.
  const [allAttributes, setAllAttributes] = useState<ProductAttribute[]>([]);
  const [assignments, setAssignments] = useState<CategoryAttributeAssignmentInput[]>([]);
  const [addAttributeId, setAddAttributeId] = useState<number | ''>('');

  const isEdit = category !== null;

  useEffect(() => {
    if (open) {
      setName(category?.name ?? '');
      setParentId(category?.parentId ?? '');
      setNameError('');
      setAssignments(
        category
          ? category.attributes
              .slice()
              .sort((a, b) => a.displayOrder - b.displayOrder)
              .map(a => ({ attributeId: a.attributeId, required: a.required }))
          : [],
      );
      setAddAttributeId('');
      ecommerceApi.listProductAttributes({ page: 0, size: 200, sortBy: 'name', sortDir: 'asc' }, showError)
        .then(result => setAllAttributes(result.content))
        .catch(() => {
          // Silent — the Attributes section just shows nothing to add; the rest of the form still works.
        });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, category]);

  // A category can't become its own descendant's child — exclude its own subtree from the picker.
  const excludeIds = isEdit ? getSubtreeIds(treeNodes, category.id) : new Set<number>();
  const flatOptions = flattenCategoryTree(treeNodes, 0, excludeIds);

  const attributeName = (id: number): string => allAttributes.find(a => a.id === id)?.name ?? `#${id}`;
  const availableToAdd = allAttributes.filter(a => !assignments.some(x => x.attributeId === a.id));

  const addAssignment = (): void => {
    if (addAttributeId === '') return;
    setAssignments([...assignments, { attributeId: addAttributeId, required: false }]);
    setAddAttributeId('');
  };

  const removeAssignment = (index: number): void => {
    setAssignments(assignments.filter((_, i) => i !== index));
  };

  const toggleRequired = (index: number): void => {
    setAssignments(assignments.map((a, i) => (i === index ? { ...a, required: !a.required } : a)));
  };

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
          const payload: UpdateProductCategoryPayload = {
            name: name.trim(), parentId: resolvedParentId, attributes: assignments,
          };
          await ecommerceApi.updateProductCategory(category.id, payload, showError);
          showSuccess('Product category updated');
        } else {
          const payload: CreateProductCategoryPayload = {
            name: name.trim(), parentId: resolvedParentId ?? undefined, attributes: assignments,
          };
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
    <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth="sm" fullWidth>
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

          <Divider />

          <Box>
            <Typography variant="body2" fontWeight={600} sx={{ mb: 0.5 }}>Attributes</Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
              Which product attributes variants in this category are expected to carry. Leave empty
              to keep this category fully free-form (no enforcement).
            </Typography>

            {assignments.length > 0 && (
              <Stack spacing={0.5} sx={{ mb: 1.5 }}>
                {assignments.map((assignment, index) => (
                  <Stack
                    key={assignment.attributeId}
                    direction="row"
                    alignItems="center"
                    spacing={0.5}
                    sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, px: 1, py: 0.5 }}
                  >
                    <Typography variant="body2" sx={{ flex: 1 }} fontWeight={500}>
                      {attributeName(assignment.attributeId)}
                    </Typography>
                    <FormControlLabel
                      sx={{ mr: 0 }}
                      control={
                        <Checkbox
                          size="small"
                          checked={assignment.required}
                          onChange={() => toggleRequired(index)}
                          disableRipple
                        />
                      }
                      label={<Typography variant="caption">Required</Typography>}
                    />
                    <Tooltip title="Move earlier">
                      <span>
                        <IconButton size="small" disabled={index === 0} onClick={() => setAssignments(move(assignments, index, -1))}>
                          <ArrowUpwardIcon fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title="Move later">
                      <span>
                        <IconButton
                          size="small"
                          disabled={index === assignments.length - 1}
                          onClick={() => setAssignments(move(assignments, index, 1))}
                        >
                          <ArrowDownwardIcon fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title="Remove">
                      <IconButton size="small" color="error" onClick={() => removeAssignment(index)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                ))}
              </Stack>
            )}

            <Stack direction="row" spacing={1}>
              <FormControl fullWidth size="small" disabled={availableToAdd.length === 0}>
                <InputLabel>Add attribute</InputLabel>
                <Select
                  label="Add attribute"
                  value={addAttributeId}
                  onChange={e => setAddAttributeId(e.target.value as number | '')}
                >
                  {availableToAdd.map(a => (
                    <MenuItem key={a.id} value={a.id}>{a.name}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Button variant="outlined" onClick={addAssignment} disabled={addAttributeId === ''}>
                Add
              </Button>
            </Stack>
          </Box>
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Cancel</Button>
        <SubmitButton saving={saving} onClick={handleSubmit} label={isEdit ? 'Save' : 'Create'} />
      </DialogActions>
    </Dialog>
  );
}
