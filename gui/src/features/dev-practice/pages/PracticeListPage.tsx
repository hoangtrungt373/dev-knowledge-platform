import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box,
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
  Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { Difficulty, ProblemSummary, ProblemTagSummary } from '../types';
import { practiceApi } from '../api/practiceApi';
import { DIFFICULTIES, DIFFICULTY_LABEL, MAX_TAG_CHIPS } from '../constants';
import { useProblemProgress } from '../hooks/useProblemProgress';
import ProgressMarker from '../components/ProgressMarker';
import DifficultyChip from '../components/DifficultyChip';
import TagChips from '../components/TagChips';
import TagFilterSelect from '../components/TagFilterSelect';
import { useNotification } from '@shared/contexts/NotificationContext';
import TableStatusRow from '@shared/components/TableStatusRow';

const PAGE_SIZE = 20;

function isDifficulty(value: string | null): value is Difficulty {
  return value !== null && (DIFFICULTIES as string[]).includes(value);
}

/**
 * The learner's problem catalog — `/practice`, public (no login needed to browse). Every filter
 * lives in the URL (`?q=&difficulty=&tag=1&tag=2&page=`), so a filtered view can be bookmarked or
 * shared and the back button returns to it from a problem. Clicking a row opens the workspace.
 */
export default function PracticeListPage(): JSX.Element {
  const navigate = useNavigate();
  const { showError } = useNotification();
  const [searchParams, setSearchParams] = useSearchParams();

  // Derived from the URL on every render — the URL is the single source of truth for filters.
  const q = searchParams.get('q') ?? '';
  const difficultyParam = searchParams.get('difficulty');
  const difficulty = isDifficulty(difficultyParam) ? difficultyParam : '';
  const tagIds = useMemo(
    () => searchParams.getAll('tag').map(Number).filter(n => Number.isInteger(n)),
    [searchParams],
  );
  const page = Math.max(0, Number(searchParams.get('page') ?? 0) || 0);

  const [searchInput, setSearchInput] = useState(q);
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [allTags, setAllTags] = useState<ProblemTagSummary[]>([]);
  const progress = useProblemProgress();

  /** Applies filter changes to the URL; any filter change starts again from page 0. */
  const updateParams = (changes: { q?: string; difficulty?: string; tags?: number[]; page?: number }) =>
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      const set = (key: string, value: string) => (value ? next.set(key, value) : next.delete(key));
      if (changes.q !== undefined) set('q', changes.q);
      if (changes.difficulty !== undefined) set('difficulty', changes.difficulty);
      if (changes.tags !== undefined) {
        next.delete('tag');
        changes.tags.forEach(id => next.append('tag', String(id)));
      }
      set('page', changes.page ? String(changes.page) : '');
      return next;
    }, { replace: true });

  useEffect(() => {
    practiceApi.listTags().then(setAllTags).catch(() => { /* the tag filter just stays empty */ });
  }, []);

  // Debounced: typing updates the URL (and refetches) 300ms after the last keystroke.
  useEffect(() => {
    if (searchInput === q) return;
    const t = setTimeout(() => updateParams({ q: searchInput.trim() }), 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchInput]);

  const tagKey = tagIds.join(',');
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    practiceApi.listProblems({
      page,
      size: PAGE_SIZE,
      q: q || undefined,
      difficulty: difficulty || undefined,
      tagIds: tagIds.length ? tagIds : undefined,
    }, showError)
      .then(data => {
        if (cancelled) return;
        setProblems(data.content);
        setTotal(data.totalElements);
      })
      .catch(() => { /* showError already called */ })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
    // tagKey stands in for tagIds (a new array instance on every URL change).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, q, difficulty, tagKey, showError]);

  return (
    <Box sx={{ p: 3, maxWidth: 1100, mx: 'auto' }}>
      <Box sx={{ mb: 2.5 }}>
        <Typography variant="h5" fontWeight={700}>Practice</Typography>
        <Typography variant="body2" color="text.secondary">
          Solve coding problems in Java, Python or JavaScript — {total} problem{total !== 1 ? 's' : ''}
          {q || difficulty || tagIds.length ? ' match your filters' : ''}.
          {progress.solvedCount > 0 && ` You've solved ${progress.solvedCount}.`}
        </Typography>
      </Box>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          placeholder="Search by title…"
          size="small"
          value={searchInput}
          onChange={e => setSearchInput(e.target.value)}
          sx={{ flex: 1 }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
        />
        <Select
          size="small"
          displayEmpty
          value={difficulty}
          onChange={e => updateParams({ difficulty: e.target.value })}
          sx={{ minWidth: 150 }}
        >
          <MenuItem value="">All difficulties</MenuItem>
          {DIFFICULTIES.map(d => <MenuItem key={d} value={d}>{DIFFICULTY_LABEL[d]}</MenuItem>)}
        </Select>
        <TagFilterSelect
          tags={allTags}
          selectedIds={tagIds}
          onChange={ids => updateParams({ tags: ids })}
          allLabel="All topics"
          sx={{ minWidth: 200, maxWidth: { sm: 300 } }}
        />
      </Stack>

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              {/* Solved / attempted marker — empty for untouched problems and for logged-out visitors. */}
              <TableCell sx={{ width: 48 }} aria-label="Status" />
              <TableCell sx={{ fontWeight: 700 }}>Title</TableCell>
              <TableCell sx={{ fontWeight: 700, width: 120 }}>Difficulty</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Topics</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            <TableStatusRow
              loading={loading}
              isEmpty={problems.length === 0}
              colSpan={4}
              emptyMessage="No problems match these filters."
            />
            {!loading && problems.map(problem => (
                <TableRow
                  key={problem.id}
                  hover
                  onClick={() => navigate(`/practice/${problem.slug}`)}
                  sx={{ cursor: 'pointer' }}
                >
                  <TableCell sx={{ pr: 0 }}>
                    <ProgressMarker status={progress.statusOf(problem.id)} />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>{problem.title}</Typography>
                  </TableCell>
                  <TableCell>
                    <DifficultyChip difficulty={problem.difficulty} />
                  </TableCell>
                  <TableCell>
                    <TagChips tags={problem.tags} max={MAX_TAG_CHIPS} />
                  </TableCell>
                </TableRow>
            ))}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={total}
          page={page}
          onPageChange={(_, p) => updateParams({ page: p })}
          rowsPerPage={PAGE_SIZE}
          rowsPerPageOptions={[PAGE_SIZE]}
        />
      </TableContainer>
    </Box>
  );
}
