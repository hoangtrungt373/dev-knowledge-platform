import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
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
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { CategoryTreeNode, ContentStatus, Difficulty, QuestionAnswer } from '../types';
import { contentApi } from '../api/contentApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import ConfirmDialog from '@shared/components/ConfirmDialog';

const PAGE_SIZE_OPTIONS = [10, 20, 50];

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

function buildCategoryNameMap(nodes: CategoryTreeNode[]): Record<number, string> {
  const map: Record<number, string> = {};
  function walk(list: CategoryTreeNode[]) {
    list.forEach(n => { map[n.id] = n.name; walk(n.children); });
  }
  walk(nodes);
  return map;
}

const DIFFICULTY_COLOR: Record<Difficulty, 'success' | 'primary' | 'warning'> = {
  BEGINNER: 'success',
  INTERMEDIATE: 'primary',
  ADVANCED: 'warning',
};

const STATUS_COLOR: Record<ContentStatus, 'default' | 'success' | 'warning'> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  ARCHIVED: 'warning',
};

export default function QuestionAnswerListPage(): JSX.Element {
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();

  const [questions, setQuestions] = useState<QuestionAnswer[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [categoryNameMap, setCategoryNameMap] = useState<Record<number, string>>({});

  // Pagination + filters
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [difficultyFilter, setDifficultyFilter] = useState<Difficulty | ''>('');
  const [statusFilter, setStatusFilter] = useState<ContentStatus | ''>('');

  // Delete dialog
  const [deleteTarget, setDeleteTarget] = useState<QuestionAnswer | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Load category tree once (for name display in table)
  useEffect(() => {
    contentApi.getCategoryTree(showError).then(nodes => {
      setCategoryNameMap(buildCategoryNameMap(nodes));
    });
  }, [showError]);

  // Debounce search
  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput); setPage(0); }, 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  const fetchQuestions = useCallback(async () => {
    setLoading(true);
    try {
      const data = await contentApi.listQuestionAnswers({
        page,
        size: pageSize,
        sortBy: 'id',
        sortDir: 'desc',
        q: search || undefined,
        difficulty: difficultyFilter || undefined,
        status: statusFilter || undefined,
      }, showError);
      setQuestions(data.content);
      setTotal(data.totalElements);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search, difficultyFilter, statusFilter, showError]);

  useEffect(() => { fetchQuestions(); }, [fetchQuestions]);

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await contentApi.deleteQuestionAnswer(deleteTarget.id, showError);
      showSuccess(`"${deleteTarget.title}" deleted`);
      setDeleteTarget(null);
      fetchQuestions();
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
          <Typography variant="h5" fontWeight={700}>Questions & Answers</Typography>
          <Typography variant="body2" color="text.secondary">
            {total} question{total !== 1 ? 's' : ''} total
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate('/admin/question-answers/new')}
        >
          New Question
        </Button>
      </Stack>

      {/* Filters */}
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
          <MenuItem value="BEGINNER">Beginner</MenuItem>
          <MenuItem value="INTERMEDIATE">Intermediate</MenuItem>
          <MenuItem value="ADVANCED">Advanced</MenuItem>
        </Select>
        <Select
          value={statusFilter}
          onChange={e => { setStatusFilter(e.target.value as ContentStatus | ''); setPage(0); }}
          displayEmpty
          size="small"
          sx={{ minWidth: 130 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          <MenuItem value="DRAFT">Draft</MenuItem>
          <MenuItem value="PUBLISHED">Published</MenuItem>
          <MenuItem value="ARCHIVED">Archived</MenuItem>
        </Select>
      </Stack>

      {/* Table */}
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>Title</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Difficulty</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Category</TableCell>
              <TableCell sx={{ fontWeight: 700 }} align="center">Common</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Created</TableCell>
              <TableCell sx={{ fontWeight: 700 }} align="right">Actions</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={28} />
                </TableCell>
              </TableRow>
            ) : questions.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                  <Typography variant="body2" color="text.secondary">
                    {search || difficultyFilter || statusFilter
                      ? 'No questions match your filters.'
                      : 'No questions yet. Create the first one.'}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              questions.map(q => (
                <TableRow key={q.id} hover>
                  <TableCell sx={{ maxWidth: 320 }}>
                    <Typography variant="body2" fontWeight={600} noWrap title={q.title}>
                      {q.title}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {q.difficulty ? (
                      <Chip
                        label={q.difficulty.charAt(0) + q.difficulty.slice(1).toLowerCase()}
                        color={DIFFICULTY_COLOR[q.difficulty]}
                        variant="outlined"
                        size="small"
                      />
                    ) : (
                      <Typography variant="body2" color="text.disabled">—</Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={q.status.charAt(0) + q.status.slice(1).toLowerCase()}
                      color={STATUS_COLOR[q.status]}
                      variant="outlined"
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {q.categoryId ? (categoryNameMap[q.categoryId] ?? `#${q.categoryId}`) : '—'}
                    </Typography>
                  </TableCell>
                  <TableCell align="center">
                    {q.isCommon
                      ? <CheckCircleIcon fontSize="small" color="success" />
                      : <Typography variant="body2" color="text.disabled">—</Typography>}
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {formatDate(q.createdAt)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit">
                      <IconButton
                        size="small"
                        onClick={() => navigate(`/admin/question-answers/${q.id}/edit`)}
                      >
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" color="error" onClick={() => setDeleteTarget(q)}>
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
        title="Delete Question"
        message={`Delete "${deleteTarget?.title}"? This cannot be undone.`}
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  );
}
