import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Box,
  Chip,
  CircularProgress,
  FormControlLabel,
  Grid,
  InputAdornment,
  List,
  ListItemButton,
  ListItemText,
  Pagination,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { ProductCategory, ProductSearchResult } from '../../types';
import { shopApi } from '../../api/shopApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useDebouncedValue } from '@shared/hooks/useDebouncedValue';
import ProductCard from '../../components/shop/ProductCard';

const PAGE_SIZE = 12;

export default function ShopPage(): JSX.Element {
  const { showError } = useNotification();

  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [results, setResults] = useState<ProductSearchResult[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput, 300);
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [minPrice, setMinPrice] = useState('');
  const [maxPrice, setMaxPrice] = useState('');
  const [inStockOnly, setInStockOnly] = useState(false);
  const [attributeFilters, setAttributeFilters] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0);

  useEffect(() => {
    shopApi.listCategories(showError).then(setCategories);
  }, [showError]);

  useEffect(() => { setPage(0); }, [search]);

  const fetchResults = useCallback(async () => {
    setLoading(true);
    try {
      const data = await shopApi.search({
        page,
        size: PAGE_SIZE,
        categoryId: categoryId ?? undefined,
        q: search || undefined,
        minPrice: minPrice ? Number(minPrice) : undefined,
        maxPrice: maxPrice ? Number(maxPrice) : undefined,
        inStockOnly,
        attributes: attributeFilters,
      }, showError);
      setResults(data.content);
      setTotal(data.totalElements);
      setTotalPages(data.totalPages);
    } finally {
      setLoading(false);
    }
  }, [page, categoryId, search, minPrice, maxPrice, inStockOnly, attributeFilters, showError]);

  useEffect(() => { fetchResults(); }, [fetchResults]);

  // Facet options are built from whatever's on the currently-loaded page of results, not the
  // whole category — there's no "every attribute value in this category" endpoint. A known,
  // page-scoped approximation, not a bug: facets may under-represent options a different page
  // of results would reveal.
  const facetOptions = useMemo(() => {
    const options: Record<string, Set<string>> = {};
    results.forEach(r => {
      Object.entries(r.availableAttributes).forEach(([key, values]) => {
        if (!options[key]) options[key] = new Set();
        values.forEach(v => options[key].add(v));
      });
    });
    return Object.fromEntries(Object.entries(options).map(([k, v]) => [k, [...v]]));
  }, [results]);

  const toggleAttributeFilter = (key: string, value: string) => {
    setPage(0);
    setAttributeFilters(prev => {
      if (prev[key] === value) {
        const next = { ...prev };
        delete next[key];
        return next;
      }
      return { ...prev, [key]: value };
    });
  };

  return (
    <Box sx={{ p: 3, maxWidth: 1400, mx: 'auto' }}>
      <Typography variant="h4" fontWeight={700} sx={{ mb: 0.5 }}>Shop</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        {total} product{total !== 1 ? 's' : ''}
      </Typography>

      <Grid container spacing={3}>

        {/* ── Sidebar: categories + filters ── */}
        <Grid item xs={12} md={3}>
          <Stack spacing={2}>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1 }}>Categories</Typography>
              <List dense disablePadding>
                <ListItemButton
                  selected={categoryId === null}
                  onClick={() => { setCategoryId(null); setPage(0); }}
                  sx={{ borderRadius: 1 }}
                >
                  <ListItemText primary="All" />
                </ListItemButton>
                {categories.map(c => (
                  <ListItemButton
                    key={c.id}
                    selected={categoryId === c.id}
                    onClick={() => { setCategoryId(c.id); setPage(0); }}
                    sx={{ borderRadius: 1 }}
                  >
                    <ListItemText primary={c.name} />
                  </ListItemButton>
                ))}
              </List>
            </Paper>

            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>Price</Typography>
              <Stack direction="row" spacing={1}>
                <TextField
                  label="Min" type="number" size="small" value={minPrice}
                  onChange={e => { setMinPrice(e.target.value); setPage(0); }}
                  inputProps={{ min: 0 }}
                />
                <TextField
                  label="Max" type="number" size="small" value={maxPrice}
                  onChange={e => { setMaxPrice(e.target.value); setPage(0); }}
                  inputProps={{ min: 0 }}
                />
              </Stack>
              <FormControlLabel
                sx={{ mt: 1.5 }}
                control={
                  <Switch
                    size="small"
                    checked={inStockOnly}
                    onChange={e => { setInStockOnly(e.target.checked); setPage(0); }}
                  />
                }
                label={<Typography variant="body2">In stock only</Typography>}
              />
            </Paper>

            {Object.keys(facetOptions).length > 0 && (
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>Attributes</Typography>
                <Stack spacing={1.5}>
                  {Object.entries(facetOptions).map(([key, values]) => (
                    <Box key={key}>
                      <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'capitalize' }}>
                        {key}
                      </Typography>
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.5 }}>
                        {values.map(value => (
                          <Chip
                            key={value}
                            label={value}
                            size="small"
                            clickable
                            color={attributeFilters[key] === value ? 'primary' : 'default'}
                            variant={attributeFilters[key] === value ? 'filled' : 'outlined'}
                            onClick={() => toggleAttributeFilter(key, value)}
                          />
                        ))}
                      </Box>
                    </Box>
                  ))}
                </Stack>
              </Paper>
            )}
          </Stack>
        </Grid>

        {/* ── Main content: search + grid ── */}
        <Grid item xs={12} md={9}>
          <TextField
            placeholder="Search products…"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            fullWidth
            sx={{ mb: 3 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" color="action" />
                </InputAdornment>
              ),
            }}
          />

          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
              <CircularProgress />
            </Box>
          ) : results.length === 0 ? (
            <Box sx={{ py: 8, textAlign: 'center' }}>
              <Typography variant="body2" color="text.secondary">No products match your filters.</Typography>
            </Box>
          ) : (
            <>
              <Grid container spacing={2}>
                {results.map(product => (
                  <Grid item xs={12} sm={6} lg={4} key={product.productId}>
                    <ProductCard product={product} />
                  </Grid>
                ))}
              </Grid>

              {totalPages > 1 && (
                <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
                  <Pagination
                    count={totalPages}
                    page={page + 1}
                    onChange={(_, p) => setPage(p - 1)}
                    color="primary"
                  />
                </Box>
              )}
            </>
          )}
        </Grid>
      </Grid>
    </Box>
  );
}
