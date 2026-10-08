import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  IconButton,
  InputAdornment,
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
import DeleteIcon from '@mui/icons-material/Delete';
import SearchIcon from '@mui/icons-material/Search';
import { AdminProblemSummary, Difficulty, ProblemStatus, ProblemTag } from '../types';
import { devPracticeApi } from '../api/devPracticeApi';
import { DIFFICULTIES, DIFFICULTY_LABEL, MAX_TAG_CHIPS, STATUSES, STATUS_COLOR, STATUS_LABEL } from '../constants';
import { formatDate } from '../utils/format';
import DifficultyChip from '../components/DifficultyChip';
import TagChips from '../components/TagChips';
import TagFilterSelect from '../components/TagFilterSelect';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useDebouncedValue } from '@shared/hooks/useDebouncedValue';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import TableStatusRow from '@shared/components/TableStatusRow';

const PAGE_SIZE_OPTIONS = [10, 20, 50];

/** Admin list of every coding problem, in any status — `/admin/problems`. */
export default function ProblemListPage(): JSX.Element {
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();

  const [problems, setProblems] = useState<AdminProblemSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput, 300);
  const [difficultyFilter, setDifficultyFilter] = useState<Difficulty | ''>('');
  const [statusFilter, setStatusFilter] = useState<ProblemStatus | ''>('');
  const [tagFilter, setTagFilter] = useState<number[]>([]);
  const [allTags, setAllTags] = useState<ProblemTag[]>([]);

  const [deleteTarget, setDeleteTarget] = useState<AdminProblemSummary | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    devPracticeApi.listAllProblemTags(showError).then(setAllTags).catch(() => {});
  }, [showError]);

  useEffect(() => { setPage(0); }, [search]);

  // Same quiet-refetch-after-delete behavior as the other admin list pages: only the
  // page/search/filter-driven load blanks the table to a spinner.
  const fetchProblems = useCallback(async (opts?: { showSpinner?: boolean }) => {
    const showSpinner = opts?.showSpinner ?? true;
    if (showSpinner) setLoading(true);
    try {
      const data = await devPracticeApi.listProblems({
        page,
        size: pageSize,
        sortBy: 'id',
        sortDir: 'desc',
        q: search || undefined,
        difficulty: difficultyFilter || undefined,
        status: statusFilter || undefined,
        tagIds: tagFilter.length === 0 ? undefined : tagFilter,
      }, showError);
      setProblems(data.content);
      setTotal(data.totalElements);
    } finally {
      if (showSpinner) setLoading(false);
    }
  }, [page, pageSize, search, difficultyFilter, statusFilter, tagFilter, showError]);

  useEffect(() => { fetchProblems(); }, [fetchProblems]);

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await devPracticeApi.deleteProblem(deleteTarget.id, showError);
      showSuccess(`"${deleteTarget.title}" deleted`);
      setDeleteTarget(null);
      fetchProblems({ showSpinner: false });
    } catch {
      // showError already called
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2.5 }}>
        <Box>
          <Typography variant="h5" fontWeight={700}>Problems</Typography>
          <Typography variant="body2" color="text.secondary">
            {total} problem{total !== 1 ? 's' : ''} total
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/admin/problems/new')}>
          New Problem
        </Button>
      </Stack>

      <Stack direction="row" spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          placeholder="Search by title…"
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
          value={difficultyFilter}
          onChange={e => { setDifficultyFilter(e.target.value as Difficulty | ''); setPage(0); }}
          displayEmpty
          size="small"
          sx={{ minWidth: 150 }}
        >
          <MenuItem value="">All difficulties</MenuItem>
          {DIFFICULTIES.map(d => <MenuItem key={d} value={d}>{DIFFICULTY_LABEL[d]}</MenuItem>)}
        </Select>
        <Select
          value={statusFilter}
          onChange={e => { setStatusFilter(e.target.value as ProblemStatus | ''); setPage(0); }}
          displayEmpty
          size="small"
          sx={{ minWidth: 130 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          {STATUSES.map(s => <MenuItem key={s} value={s}>{STATUS_LABEL[s]}</MenuItem>)}
        </Select>
        <TagFilterSelect
          tags={allTags}
          selectedIds={tagFilter}
          onChange={ids => { setTagFilter(ids); setPage(0); }}
          allLabel="All tags"
          sx={{ minWidth: 180, maxWidth: 320 }}
        />
      </Stack>

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>Title</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Difficulty</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Tags</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Published</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Created</TableCell>
              <TableCell sx={{ fontWeight: 700 }} align="right">Actions</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading || problems.length === 0 ? (
              <TableStatusRow
                loading={loading}
                isEmpty={problems.length === 0}
                emptyMessage={
                  search || difficultyFilter || statusFilter || tagFilter.length > 0
                    ? 'No problems match your filters.'
                    : 'No problems yet. Create the first one.'
                }
                colSpan={7}
              />
            ) : (
              problems.map(p => (
                <TableRow key={p.id} hover>
                  <TableCell sx={{ maxWidth: 360 }}>
                    <Typography variant="body2" fontWeight={600} noWrap title={p.title}>{p.title}</Typography>
                    <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                      {p.slug}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <DifficultyChip difficulty={p.difficulty} />
                  </TableCell>
                  <TableCell sx={{ maxWidth: 260 }}>
                    <TagChips tags={p.tags} max={MAX_TAG_CHIPS} emptyDash />
                  </TableCell>
                  <TableCell>
                    <Chip label={STATUS_LABEL[p.status]} color={STATUS_COLOR[p.status]} variant="outlined" size="small" />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">{formatDate(p.publishedAt)}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">{formatDate(p.createdAt)}</Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => navigate(`/admin/problems/${p.id}/edit`)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" color="error" onClick={() => setDeleteTarget(p)}>
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

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete Problem"
        message={`Delete "${deleteTarget?.title}"? Its test cases are deleted too. This cannot be undone.`}
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  );
}
