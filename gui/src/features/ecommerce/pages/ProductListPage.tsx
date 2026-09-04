import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Checkbox,
  Chip,
  IconButton,
  InputAdornment,
  ListItemText,
  MenuItem,
  Paper,
  Select,
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
import BlockIcon from '@mui/icons-material/Block';
import SearchIcon from '@mui/icons-material/Search';
import { Product, ProductCategory, ProductTag } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import { useDebouncedValue } from '@shared/hooks/useDebouncedValue';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import AdminListHeader from '../components/AdminListHeader';
import TableStatusRow from '@shared/components/TableStatusRow';
import { formatDate, PAGE_SIZE_OPTIONS } from '../utils/format';

export default function ProductListPage(): JSX.Element {
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();

  const [products, setProducts] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [tags, setTags] = useState<ProductTag[]>([]);

  // Pagination + filters
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput, 300);
  const [categoryFilter, setCategoryFilter] = useState<number | ''>('');
  const [activeFilter, setActiveFilter] = useState<'true' | 'false' | ''>('');
  const [tagFilter, setTagFilter] = useState<number[]>([]);

  // Deactivate dialog
  const [deactivateTarget, setDeactivateTarget] = useState<Product | null>(null);
  const { loading: deactivating, guard: guardDeactivate } = useSubmitGuard();

  useEffect(() => {
    ecommerceApi.listProductCategories(undefined, showError).then(setCategories).catch(() => {
      // Silent — a failed fetch just leaves the category filter empty; showError already fired.
    });
    ecommerceApi.listProductTags({ size: 1000, sortBy: 'name', sortDir: 'asc' }, showError)
      .then(page => setTags(page.content))
      .catch(() => {
        // Silent — a failed fetch just leaves the tag filter empty; showError already fired.
      });
  }, [showError]);

  useEffect(() => { setPage(0); }, [search]);

  const fetchProducts = useCallback(async (opts?: { showSpinner?: boolean }) => {
    const showSpinner = opts?.showSpinner ?? true;
    if (showSpinner) setLoading(true);
    try {
      const data = await ecommerceApi.listProducts({
        page,
        size: pageSize,
        sortBy: 'id',
        sortDir: 'desc',
        q: search || undefined,
        productCategoryId: categoryFilter === '' ? undefined : categoryFilter,
        active: activeFilter === '' ? undefined : activeFilter === 'true',
        tagIds: tagFilter.length === 0 ? undefined : tagFilter,
      }, showError);
      setProducts(data.content);
      setTotal(data.totalElements);
    } finally {
      if (showSpinner) setLoading(false);
    }
  }, [page, pageSize, search, categoryFilter, activeFilter, tagFilter, showError]);

  useEffect(() => { fetchProducts(); }, [fetchProducts]);

  const handleDeactivate = (): void => {
    if (!deactivateTarget) return;
    guardDeactivate(async () => {
      try {
        await ecommerceApi.deactivateProduct(deactivateTarget.id, showError);
        showSuccess(`"${deactivateTarget.name}" deactivated`);
        setDeactivateTarget(null);
        fetchProducts({ showSpinner: false });
      } catch {
        // showError already called
      }
    });
  };

  return (
    <Box sx={{ p: 3 }}>

      <AdminListHeader
        title="Products"
        subtitle={`${total} product${total !== 1 ? 's' : ''} total`}
        action={{ label: 'New Product', icon: <AddIcon />, onClick: () => navigate('/admin/products/new') }}
      />

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
        <Select
          value={categoryFilter}
          onChange={e => { setCategoryFilter(e.target.value as number | ''); setPage(0); }}
          displayEmpty
          size="small"
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">All categories</MenuItem>
          {categories.map(c => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
        </Select>
        <Select
          value={activeFilter}
          onChange={e => { setActiveFilter(e.target.value as 'true' | 'false' | ''); setPage(0); }}
          displayEmpty
          size="small"
          sx={{ minWidth: 130 }}
        >
          <MenuItem value="">All products</MenuItem>
          <MenuItem value="true">Active</MenuItem>
          <MenuItem value="false">Inactive</MenuItem>
        </Select>
        <Select
          multiple
          value={tagFilter}
          onChange={e => {
            const value = e.target.value;
            setTagFilter(typeof value === 'string' ? [] : value as number[]);
            setPage(0);
          }}
          displayEmpty
          size="small"
          sx={{ minWidth: 180 }}
          renderValue={selected =>
            selected.length === 0
              ? 'All tags'
              : tags.filter(t => selected.includes(t.id)).map(t => t.name).join(', ')
          }
        >
          {tags.map(tag => (
            <MenuItem key={tag.id} value={tag.id}>
              <Checkbox size="small" checked={tagFilter.includes(tag.id)} sx={{ p: 0.5, mr: 1 }} />
              <ListItemText primary={tag.name} />
            </MenuItem>
          ))}
        </Select>
      </Stack>

      {/* Table */}
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>Name</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Category</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
              <TableCell sx={{ fontWeight: 700 }} align="center">Variants</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Created</TableCell>
              <TableCell sx={{ fontWeight: 700 }} align="right">Actions</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading || products.length === 0 ? (
              <TableStatusRow
                loading={loading}
                isEmpty={products.length === 0}
                emptyMessage={
                  search || categoryFilter || activeFilter || tagFilter.length > 0
                    ? 'No products match your filters.'
                    : 'No products yet. Create the first one.'
                }
                colSpan={6}
              />
            ) : (
              products.map(product => (
                <TableRow key={product.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>{product.name}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">{product.categoryName}</Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={product.active ? 'Active' : 'Inactive'}
                      color={product.active ? 'success' : 'default'}
                      variant="outlined"
                      size="small"
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Typography variant="body2" color="text.secondary">{product.variants.length}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">{formatDate(product.createdAt)}</Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => navigate(`/admin/products/${product.id}/edit`)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title={product.active ? 'Deactivate' : 'Already inactive'}>
                      <span>
                        <IconButton
                          size="small"
                          color="warning"
                          disabled={!product.active}
                          onClick={() => setDeactivateTarget(product)}
                        >
                          <BlockIcon fontSize="small" />
                        </IconButton>
                      </span>
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

      <ConfirmDialog
        open={deactivateTarget !== null}
        title="Deactivate Product"
        message={`Deactivate "${deactivateTarget?.name}"? It will disappear from browse/search — existing orders and reviews are unaffected.`}
        confirmLabel="Deactivate"
        loading={deactivating}
        onConfirm={handleDeactivate}
        onCancel={() => setDeactivateTarget(null)}
      />
    </Box>
  );
}
