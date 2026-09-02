import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Chip,
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
import { ProductAttribute } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import { useDebouncedValue } from '@shared/hooks/useDebouncedValue';
import ProductAttributeFormDialog from '../components/ProductAttributeFormDialog';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import AdminListHeader from '../components/AdminListHeader';
import TableStatusRow from '../components/TableStatusRow';
import { formatDate, PAGE_SIZE_OPTIONS } from '../utils/format';

/** Mirrors `ProductTagListPage` — the "Option B" global attribute registry's admin CRUD surface,
 * plus a Values column (chips, in display order) `ProductTag` has no equivalent of. Category
 * assignment isn't managed here — it travels with `ProductCategoryFormDialog`'s own Attributes
 * section instead (mirrors `ProductApi`'s own "assignment travels with create/update" convention
 * for tags). */
export default function ProductAttributeListPage(): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [attributes, setAttributes] = useState<ProductAttribute[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput, 300);

  const [formOpen, setFormOpen] = useState(false);
  const [editAttribute, setEditAttribute] = useState<ProductAttribute | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ProductAttribute | null>(null);
  const { loading: deleting, guard: guardDelete } = useSubmitGuard();

  useEffect(() => { setPage(0); }, [search]);

  const fetchAttributes = useCallback(async (opts?: { showSpinner?: boolean }) => {
    const showSpinner = opts?.showSpinner ?? true;
    if (showSpinner) setLoading(true);
    try {
      const data = await ecommerceApi.listProductAttributes({
        page,
        size: pageSize,
        sortBy: 'name',
        sortDir: 'asc',
        q: search || undefined,
      }, showError);
      setAttributes(data.content);
      setTotal(data.totalElements);
    } finally {
      if (showSpinner) setLoading(false);
    }
  }, [page, pageSize, search, showError]);

  useEffect(() => { fetchAttributes(); }, [fetchAttributes]);

  const refreshAttributes = useCallback(() => fetchAttributes({ showSpinner: false }), [fetchAttributes]);

  const openCreate = () => { setEditAttribute(null); setFormOpen(true); };
  const openEdit = (attribute: ProductAttribute) => { setEditAttribute(attribute); setFormOpen(true); };

  const handleDelete = (): void => {
    if (!deleteTarget) return;
    guardDelete(async () => {
      try {
        await ecommerceApi.deleteProductAttribute(deleteTarget.id, showError);
        showSuccess(`Product attribute "${deleteTarget.name}" deleted`);
        setDeleteTarget(null);
        refreshAttributes();
      } catch {
        // showError already called
      }
    });
  };

  return (
    <Box sx={{ p: 3 }}>

      <AdminListHeader
        title="Product Attributes"
        subtitle={`${total} attribute${total !== 1 ? 's' : ''} total`}
        action={{ label: 'New Product Attribute', icon: <AddIcon />, onClick: openCreate }}
      />

      {/* Filters */}
      <Stack direction="row" spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          placeholder="Search by name…"
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
              <TableCell sx={{ fontWeight: 700 }}>Values</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Created</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>Actions</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading || attributes.length === 0 ? (
              <TableStatusRow
                loading={loading}
                isEmpty={attributes.length === 0}
                emptyMessage={search ? 'No product attributes match your search.' : 'No product attributes yet. Create the first one.'}
                colSpan={4}
              />
            ) : (
              attributes.map(attribute => (
                <TableRow key={attribute.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>{attribute.name}</Typography>
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                      {attribute.values
                        .slice()
                        .sort((a, b) => a.displayOrder - b.displayOrder)
                        .map(value => (
                          <Chip key={value.id} label={value.value} size="small" variant="outlined" />
                        ))}
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {formatDate(attribute.createdAt)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => openEdit(attribute)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" color="error" onClick={() => setDeleteTarget(attribute)}>
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
      <ProductAttributeFormDialog
        open={formOpen}
        attribute={editAttribute}
        onClose={() => setFormOpen(false)}
        onSaved={refreshAttributes}
      />

      {/* Delete confirmation */}
      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete Product Attribute"
        message={`Delete "${deleteTarget?.name}"? This cannot be undone. Attributes still assigned to a category can't be deleted.`}
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  );
}
