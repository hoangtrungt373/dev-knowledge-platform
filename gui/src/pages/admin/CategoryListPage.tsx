import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  IconButton,
  InputAdornment,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import SearchIcon from '@mui/icons-material/Search';
import { Category, CategoryTreeNode } from '../../types/admin.types';
import { adminApi } from '../../api/adminApi';
import { useNotification } from '../../contexts/NotificationContext';
import CategoryFormDialog from '../../components/admin/CategoryFormDialog';
import ConfirmDialog from '../../components/admin/ConfirmDialog';

const PAGE_SIZE_OPTIONS = [10, 20, 50];

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
  });
}

function buildNameMap(nodes: CategoryTreeNode[]): Record<number, string> {
  const map: Record<number, string> = {};
  function walk(list: CategoryTreeNode[]) {
    list.forEach(n => { map[n.id] = n.name; walk(n.children); });
  }
  walk(nodes);
  return map;
}

export default function CategoryListPage(): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [categories, setCategories] = useState<Category[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [treeNodes, setTreeNodes] = useState<CategoryTreeNode[]>([]);
  const [parentNameMap, setParentNameMap] = useState<Record<number, string>>({});

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editCategory, setEditCategory] = useState<Category | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);
  const [deleting, setDeleting] = useState(false);

  const loadTree = useCallback(() => {
    adminApi.getCategoryTree(showError).then(nodes => {
      setTreeNodes(nodes);
      setParentNameMap(buildNameMap(nodes));
    });
  }, [showError]);

  useEffect(() => { loadTree(); }, [loadTree]);

  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput); setPage(0); }, 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  const fetchCategories = useCallback(async () => {
    setLoading(true);
    try {
      const data = await adminApi.listCategories(
        { page, size: pageSize, sortBy: 'name', sortDir: 'asc', q: search || undefined },
        showError,
      );
      setCategories(data.content);
      setTotal(data.totalElements);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search, showError]);

  useEffect(() => { fetchCategories(); }, [fetchCategories]);

  const refreshAll = () => { fetchCategories(); loadTree(); };

  const openCreate = () => { setEditCategory(null); setFormOpen(true); };
  const openEdit = (cat: Category) => { setEditCategory(cat); setFormOpen(true); };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await adminApi.deleteCategory(deleteTarget.id, showError);
      showSuccess(`Category "${deleteTarget.name}" deleted`);
      setDeleteTarget(null);
      refreshAll();
    } catch {
      // showError already called
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Box sx={{ p: 3 }}>

      {/* Header */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2.5 }}>
        <Box>
          <Typography variant="h5" fontWeight={700}>Categories</Typography>
          <Typography variant="body2" color="text.secondary">
            {total} categor{total !== 1 ? 'ies' : 'y'} total
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
          New Category
        </Button>
      </Stack>

      {/* Search */}
      <Stack direction="row" spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          placeholder="Search by name or slug…"
          value={searchInput}
          onChange={e => setSearchInput(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" color="action" />
              </InputAdornment>
            ),
          }}
          sx={{ width: 280 }}
        />
      </Stack>

      {/* Table */}
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>Name</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Slug</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Parent</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Created</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>Actions</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={28} />
                </TableCell>
              </TableRow>
            ) : categories.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 6 }}>
                  <Typography variant="body2" color="text.secondary">
                    {search ? 'No categories match your search.' : 'No categories yet. Create the first one.'}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              categories.map(cat => (
                <TableRow key={cat.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>{cat.name}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                      {cat.slug}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {cat.parentId ? (parentNameMap[cat.parentId] ?? `#${cat.parentId}`) : '—'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {formatDate(cat.createdAt)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => openEdit(cat)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" color="error" onClick={() => setDeleteTarget(cat)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>

        <TablePagination
          component="div"
          count={total}
          page={page}
          rowsPerPage={pageSize}
          rowsPerPageOptions={PAGE_SIZE_OPTIONS}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={e => { setPageSize(Number(e.target.value)); setPage(0); }}
        />
      </TableContainer>

      <CategoryFormDialog
        open={formOpen}
        category={editCategory}
        treeNodes={treeNodes}
        onClose={() => setFormOpen(false)}
        onSaved={refreshAll}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete Category"
        message={`Delete "${deleteTarget?.name}"? Any child categories will become root-level.`}
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  );
}
