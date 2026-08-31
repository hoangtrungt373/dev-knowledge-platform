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
import { ProductTag } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import ProductTagFormDialog from '../components/ProductTagFormDialog';
import ConfirmDialog from '@shared/components/ConfirmDialog';

const PAGE_SIZE_OPTIONS = [10, 20, 50];

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
  });
}

/** Mirrors @content's TagListPage — minus the Status filter/column, since ProductTag has none
 * (per the confirmed "just name + slug" scope, see ecommerce-service/CLAUDE.md). */
export default function ProductTagListPage(): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [tags, setTags] = useState<ProductTag[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editTag, setEditTag] = useState<ProductTag | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ProductTag | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => {
      setSearch(searchInput);
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  const fetchTags = useCallback(async (opts?: { showSpinner?: boolean }) => {
    const showSpinner = opts?.showSpinner ?? true;
    if (showSpinner) setLoading(true);
    try {
      const data = await ecommerceApi.listProductTags({
        page,
        size: pageSize,
        sortBy: 'name',
        sortDir: 'asc',
        q: search || undefined,
      }, showError);
      setTags(data.content);
      setTotal(data.totalElements);
    } finally {
      if (showSpinner) setLoading(false);
    }
  }, [page, pageSize, search, showError]);

  useEffect(() => { fetchTags(); }, [fetchTags]);

  const refreshTags = useCallback(() => fetchTags({ showSpinner: false }), [fetchTags]);

  const openCreate = () => { setEditTag(null); setFormOpen(true); };
  const openEdit = (tag: ProductTag) => { setEditTag(tag); setFormOpen(true); };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await ecommerceApi.deleteProductTag(deleteTarget.id, showError);
      showSuccess(`Product tag "${deleteTarget.name}" deleted`);
      setDeleteTarget(null);
      refreshTags();
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
          <Typography variant="h5" fontWeight={700}>Product Tags</Typography>
          <Typography variant="body2" color="text.secondary">
            {total} tag{total !== 1 ? 's' : ''} total
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
          New Product Tag
        </Button>
      </Stack>

      {/* Filters */}
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
              <TableCell sx={{ fontWeight: 700 }}>Created</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>Actions</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={4} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={28} />
                </TableCell>
              </TableRow>
            ) : tags.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} align="center" sx={{ py: 6 }}>
                  <Typography variant="body2" color="text.secondary">
                    {search ? 'No product tags match your search.' : 'No product tags yet. Create the first one.'}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              tags.map(tag => (
                <TableRow key={tag.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>{tag.name}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                      {tag.slug}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {formatDate(tag.createdAt)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => openEdit(tag)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" color="error" onClick={() => setDeleteTarget(tag)}>
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

      {/* Form dialog */}
      <ProductTagFormDialog
        open={formOpen}
        tag={editTag}
        onClose={() => setFormOpen(false)}
        onSaved={refreshTags}
      />

      {/* Delete confirmation */}
      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete Product Tag"
        message={`Delete "${deleteTarget?.name}"? This cannot be undone. Tags still assigned to a product can't be deleted.`}
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  );
}
