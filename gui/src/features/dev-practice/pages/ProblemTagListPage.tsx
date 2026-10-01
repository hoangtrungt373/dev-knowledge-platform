import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
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
import { ProblemTag } from '../types';
import { devPracticeApi } from '../api/devPracticeApi';
import ProblemTagFormDialog from '../components/ProblemTagFormDialog';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import { useDebouncedValue } from '@shared/hooks/useDebouncedValue';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import TableStatusRow from '@shared/components/TableStatusRow';

const PAGE_SIZE_OPTIONS = [10, 20, 50];

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

/** Admin catalog of problem tags (topics) — `/admin/problem-tags`. Mirrors @ecommerce's
 * ProductTagListPage: search, create/rename in a dialog, delete refused server-side while in use. */
export default function ProblemTagListPage(): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [tags, setTags] = useState<ProblemTag[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput, 300);

  const [formOpen, setFormOpen] = useState(false);
  const [editTag, setEditTag] = useState<ProblemTag | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ProblemTag | null>(null);
  const { loading: deleting, guard: guardDelete } = useSubmitGuard();

  useEffect(() => { setPage(0); }, [search]);

  const fetchTags = useCallback(async (opts?: { showSpinner?: boolean }) => {
    const showSpinner = opts?.showSpinner ?? true;
    if (showSpinner) setLoading(true);
    try {
      const data = await devPracticeApi.listProblemTags(
        { page, size: pageSize, sortBy: 'name', sortDir: 'asc', q: search || undefined },
        showError,
      );
      setTags(data.content);
      setTotal(data.totalElements);
    } finally {
      if (showSpinner) setLoading(false);
    }
  }, [page, pageSize, search, showError]);

  useEffect(() => { fetchTags(); }, [fetchTags]);

  const refreshTags = useCallback(() => fetchTags({ showSpinner: false }), [fetchTags]);

  const handleDelete = (): void => {
    if (!deleteTarget) return;
    guardDelete(async () => {
      try {
        await devPracticeApi.deleteProblemTag(deleteTarget.id, showError);
        showSuccess(`Tag "${deleteTarget.name}" deleted`);
        setDeleteTarget(null);
        refreshTags();
      } catch {
        // showError already called (e.g. PROBLEM_TAG_IN_USE)
      }
    });
  };

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2.5 }}>
        <Box>
          <Typography variant="h5" fontWeight={700}>Problem Tags</Typography>
          <Typography variant="body2" color="text.secondary">
            {total} tag{total !== 1 ? 's' : ''} total
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditTag(null); setFormOpen(true); }}>
          New Tag
        </Button>
      </Stack>

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
            {loading || tags.length === 0 ? (
              <TableStatusRow
                loading={loading}
                isEmpty={tags.length === 0}
                emptyMessage={search ? 'No tags match your search.' : 'No tags yet. Create the first one.'}
                colSpan={4}
              />
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
                    <Typography variant="body2" color="text.secondary">{formatDate(tag.createdAt)}</Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => { setEditTag(tag); setFormOpen(true); }}>
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

      <ProblemTagFormDialog open={formOpen} tag={editTag} onClose={() => setFormOpen(false)} onSaved={refreshTags} />

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete Tag"
        message={`Delete "${deleteTarget?.name}"? A tag still used by a problem can't be deleted — remove it from those problems first.`}
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  );
}
