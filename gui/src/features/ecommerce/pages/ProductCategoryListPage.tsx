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
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import SearchIcon from '@mui/icons-material/Search';
import { ProductCategory, ProductCategoryTreeNode } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import ProductCategoryFormDialog from '../components/ProductCategoryFormDialog';

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
  });
}

function buildNameMap(nodes: ProductCategoryTreeNode[]): Record<number, string> {
  const map: Record<number, string> = {};
  function walk(list: ProductCategoryTreeNode[]) {
    list.forEach(n => { map[n.id] = n.name; walk(n.children); });
  }
  walk(nodes);
  return map;
}

// Flat, unpaginated list — ProductCategoryApi.list has no page/size params (this taxonomy is
// expected to stay small, same assumption the backend's own Javadoc makes, even with a hierarchy).
// No delete action — ProductCategoryApi doesn't expose one (see ecommerce-service/CLAUDE.md).
export default function ProductCategoryListPage(): JSX.Element {
  const { showError } = useNotification();

  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [treeNodes, setTreeNodes] = useState<ProductCategoryTreeNode[]>([]);
  const [parentNameMap, setParentNameMap] = useState<Record<number, string>>({});

  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editCategory, setEditCategory] = useState<ProductCategory | null>(null);

  const loadTree = useCallback(() => {
    ecommerceApi.getProductCategoryTree(showError).then(nodes => {
      setTreeNodes(nodes);
      setParentNameMap(buildNameMap(nodes));
    });
  }, [showError]);

  useEffect(() => { loadTree(); }, [loadTree]);

  useEffect(() => {
    const t = setTimeout(() => setSearch(searchInput), 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  const fetchCategories = useCallback(async (opts?: { showSpinner?: boolean }) => {
    const showSpinner = opts?.showSpinner ?? true;
    if (showSpinner) setLoading(true);
    try {
      const data = await ecommerceApi.listProductCategories(search || undefined, showError);
      setCategories(data);
    } finally {
      if (showSpinner) setLoading(false);
    }
  }, [search, showError]);

  useEffect(() => { fetchCategories(); }, [fetchCategories]);

  const refreshAll = () => { fetchCategories({ showSpinner: false }); loadTree(); };

  const openCreate = () => { setEditCategory(null); setFormOpen(true); };
  const openEdit = (category: ProductCategory) => { setEditCategory(category); setFormOpen(true); };

  return (
    <Box sx={{ p: 3 }}>

      {/* Header */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2.5 }}>
        <Box>
          <Typography variant="h5" fontWeight={700}>Product Categories</Typography>
          <Typography variant="body2" color="text.secondary">
            {categories.length} categor{categories.length !== 1 ? 'ies' : 'y'} total
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
          New Category
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
                    {search ? 'No categories match your search.' : 'No product categories yet. Create the first one.'}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              categories.map(category => (
                <TableRow key={category.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>{category.name}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                      {category.slug}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {category.parentId ? (parentNameMap[category.parentId] ?? `#${category.parentId}`) : '—'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {formatDate(category.createdAt)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => openEdit(category)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Form dialog */}
      <ProductCategoryFormDialog
        open={formOpen}
        category={editCategory}
        treeNodes={treeNodes}
        onClose={() => setFormOpen(false)}
        onSaved={refreshAll}
      />
    </Box>
  );
}
