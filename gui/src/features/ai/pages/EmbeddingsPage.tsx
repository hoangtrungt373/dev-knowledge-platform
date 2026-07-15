import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  InputAdornment,
  MenuItem,
  Paper,
  Select,
  Skeleton,
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
import SearchIcon from '@mui/icons-material/Search';
import RefreshIcon from '@mui/icons-material/Refresh';
import ReplayIcon from '@mui/icons-material/Replay';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import AllInclusiveIcon from '@mui/icons-material/AllInclusive';
import HubIcon from '@mui/icons-material/Hub';
import { monitoringApi } from '../api/monitoringApi';
import { EmbeddingIndexItem } from '../types';
import { EmbeddingContentType } from '@content/types';
import { useNotification } from '@shared/contexts/NotificationContext';

// ── Formatters ────────────────────────────────────────────────────────────────

function fmtTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

function fmtDate(iso: string | null): string {
  if (!iso) return '—';
  const date = new Date(iso);
  const diffMs = Date.now() - date.getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH}h ago`;
  const diffD = Math.floor(diffH / 24);
  if (diffD < 30) return `${diffD}d ago`;
  return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

function fmtQuality(score: number | null): string {
  if (score === null) return '—';
  return `${(score * 100).toFixed(1)}%`;
}

// ── Small UI atoms ─────────────────────────────────────────────────────────────

const CONTENT_TYPE_LABELS: Record<EmbeddingContentType, string> = {
  ARTICLE: 'Article',
  BLOG_POST: 'Blog Post',
  QUESTION_ANSWER: 'Question',
};

const CONTENT_TYPE_COLORS: Record<EmbeddingContentType, 'primary' | 'secondary' | 'info'> = {
  ARTICLE: 'primary',
  BLOG_POST: 'secondary',
  QUESTION_ANSWER: 'info',
};

function TypeChip({ type }: { type: EmbeddingContentType }) {
  return (
    <Chip
      label={CONTENT_TYPE_LABELS[type] ?? type}
      color={CONTENT_TYPE_COLORS[type] ?? 'default'}
      size="small"
      variant="outlined"
      sx={{ fontSize: '0.7rem', height: 20 }}
    />
  );
}

function StatusChip({ status }: { status: string }) {
  const color = status === 'PUBLISHED' ? 'success' : status === 'ARCHIVED' ? 'warning' : 'default';
  const label = status === 'PUBLISHED' ? 'Published' : status === 'ARCHIVED' ? 'Archived' : 'Draft';
  return (
    <Chip
      label={label}
      color={color as any}
      size="small"
      variant="outlined"
      sx={{ fontSize: '0.7rem', height: 20 }}
    />
  );
}

function IndexedChip({ indexed }: { indexed: boolean }) {
  return (
    <Chip
      label={indexed ? 'Indexed' : 'Not indexed'}
      color={indexed ? 'success' : 'default'}
      size="small"
      sx={{ fontSize: '0.7rem', height: 20 }}
    />
  );
}

function QualityBadge({ score }: { score: number | null }) {
  if (score === null) {
    return <Typography variant="caption" color="text.disabled">—</Typography>;
  }
  const color =
    score >= 0.85 ? 'success.main'
    : score >= 0.70 ? 'warning.main'
    : 'error.main';
  return (
    <Typography variant="caption" fontWeight={600} sx={{ color }}>
      {fmtQuality(score)}
    </Typography>
  );
}

// ── Confirm dialog ────────────────────────────────────────────────────────────

type ActionKind = 'reindex' | 'deleteIndex' | 'indexAll' | 'refreshCorpus';

interface PendingAction {
  kind: ActionKind;
  contentItemId?: number;
  title?: string;
}

const ACTION_META: Record<ActionKind, { dialogTitle: string; getMessage: (title?: string) => string; btnLabel: string; btnColor: 'primary' | 'error' | 'warning' }> = {
  reindex: {
    dialogTitle: 'Re-index content item?',
    getMessage: (title) => `Re-indexing will delete all existing embeddings for "${title}" and rebuild them. This may take a few seconds.`,
    btnLabel: 'Re-index',
    btnColor: 'primary',
  },
  deleteIndex: {
    dialogTitle: 'Delete embedding index?',
    getMessage: (title) => `All embeddings for "${title}" will be permanently removed. The content item itself is not affected.`,
    btnLabel: 'Delete index',
    btnColor: 'error',
  },
  indexAll: {
    dialogTitle: 'Index all published content?',
    getMessage: () => 'All published content items will be (re-)indexed. This is a long-running background operation.',
    btnLabel: 'Index all',
    btnColor: 'primary',
  },
  refreshCorpus: {
    dialogTitle: 'Refresh corpus centroid?',
    getMessage: () => 'The corpus centroid will be recomputed from all current embeddings. This is used for quality scoring and anomaly detection.',
    btnLabel: 'Refresh',
    btnColor: 'primary',
  },
};

interface ConfirmDialogProps {
  action: PendingAction | null;
  loading: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

function ConfirmDialog({ action, loading, onConfirm, onCancel }: ConfirmDialogProps) {
  if (!action) return null;
  const meta = ACTION_META[action.kind];
  return (
    <Dialog open onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>{meta.dialogTitle}</DialogTitle>
      <DialogContent>
        <DialogContentText>{meta.getMessage(action.title)}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={loading}>Cancel</Button>
        <Button
          onClick={onConfirm}
          color={meta.btnColor}
          variant="contained"
          disabled={loading}
          startIcon={loading ? <CircularProgress size={14} color="inherit" /> : undefined}
        >
          {meta.btnLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

const CONTENT_TYPES = ['ARTICLE', 'BLOG_POST', 'QUESTION_ANSWER'];
const CONTENT_STATUSES = ['DRAFT', 'PUBLISHED', 'ARCHIVED'];

/** Admin page for viewing and managing the RAG content embedding index. */
export default function EmbeddingsPage(): JSX.Element {
  const { showError, showSuccess } = useNotification();

  // Filter state
  const [q, setQ] = useState('');
  const [contentType, setContentType] = useState('');
  const [contentStatus, setContentStatus] = useState('');
  const [indexedFilter, setIndexedFilter] = useState('');

  // Pagination
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);

  // Data
  const [items, setItems] = useState<EmbeddingIndexItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  // Action state
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await monitoringApi.listEmbeddings({
        page,
        size: rowsPerPage,
        q: q || undefined,
        contentType: contentType || undefined,
        contentStatus: contentStatus || undefined,
        indexed: indexedFilter === 'true' ? true : indexedFilter === 'false' ? false : undefined,
      }, showError);
      setItems(result.content);
      setTotal(result.totalElements);
    } catch {
      // showError already called
    } finally {
      setLoading(false);
    }
  }, [page, rowsPerPage, q, contentType, contentStatus, indexedFilter, showError]);

  useEffect(() => { load(); }, [load]);

  // Reset to page 0 when filters change
  useEffect(() => { setPage(0); }, [q, contentType, contentStatus, indexedFilter]);

  const handleConfirm = async () => {
    if (!pendingAction) return;
    setActionLoading(true);
    try {
      switch (pendingAction.kind) {
        case 'reindex':
          await monitoringApi.reindexContent(pendingAction.contentItemId!, showError);
          showSuccess('Re-indexing complete.');
          break;
        case 'deleteIndex':
          await monitoringApi.deleteContentIndex(pendingAction.contentItemId!, showError);
          showSuccess('Embedding index deleted.');
          break;
        case 'indexAll':
          await monitoringApi.indexAll(showError);
          showSuccess('Bulk indexing started.');
          break;
        case 'refreshCorpus':
          await monitoringApi.refreshCorpus(showError);
          showSuccess('Corpus centroid refreshed.');
          break;
      }
      setPendingAction(null);
      load();
    } catch {
      // showError already called
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      {/* ── Header ── */}
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" flexWrap="wrap" gap={2} sx={{ mb: 2.5 }}>
        <Box>
          <Typography variant="h5" fontWeight={700}>Embedding Index</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
            Manage RAG vector embeddings for content items
          </Typography>
        </Box>

        <Stack direction="row" spacing={1} alignItems="center">
          <Tooltip title="Refresh corpus centroid">
            <Button
              size="small"
              variant="outlined"
              startIcon={<HubIcon fontSize="small" />}
              onClick={() => setPendingAction({ kind: 'refreshCorpus' })}
            >
              Refresh Corpus
            </Button>
          </Tooltip>
          <Button
            size="small"
            variant="contained"
            startIcon={<AllInclusiveIcon fontSize="small" />}
            onClick={() => setPendingAction({ kind: 'indexAll' })}
          >
            Index All
          </Button>
        </Stack>
      </Stack>

      {/* ── Filters ── */}
      <Stack direction="row" spacing={1.5} flexWrap="wrap" sx={{ mb: 2 }} alignItems="center">
        <TextField
          size="small"
          placeholder="Search title…"
          value={q}
          onChange={e => setQ(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" sx={{ color: 'text.secondary' }} />
              </InputAdornment>
            ),
          }}
          sx={{ width: 220 }}
        />

        <Select
          size="small"
          value={contentType}
          onChange={e => setContentType(e.target.value)}
          displayEmpty
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All types</MenuItem>
          {CONTENT_TYPES.map(t => (
            <MenuItem key={t} value={t}>
              {CONTENT_TYPE_LABELS[t as EmbeddingContentType] ?? t}
            </MenuItem>
          ))}
        </Select>

        <Select
          size="small"
          value={contentStatus}
          onChange={e => setContentStatus(e.target.value)}
          displayEmpty
          sx={{ minWidth: 150 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          {CONTENT_STATUSES.map(s => (
            <MenuItem key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</MenuItem>
          ))}
        </Select>

        <Select
          size="small"
          value={indexedFilter}
          onChange={e => setIndexedFilter(e.target.value)}
          displayEmpty
          sx={{ minWidth: 140 }}
        >
          <MenuItem value="">All items</MenuItem>
          <MenuItem value="true">Indexed</MenuItem>
          <MenuItem value="false">Not indexed</MenuItem>
        </Select>

        <Tooltip title="Refresh list">
          <IconButton size="small" onClick={load} disabled={loading}>
            {loading ? <CircularProgress size={15} /> : <RefreshIcon fontSize="small" />}
          </IconButton>
        </Tooltip>
      </Stack>

      {/* ── Table ── */}
      <Paper variant="outlined">
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>Title</TableCell>
                <TableCell sx={{ fontWeight: 600 }} width={110}>Type</TableCell>
                <TableCell sx={{ fontWeight: 600 }} width={100}>Status</TableCell>
                <TableCell sx={{ fontWeight: 600, textAlign: 'right' }} width={80}>Quality</TableCell>
                <TableCell sx={{ fontWeight: 600, textAlign: 'right' }} width={70}>Chunks</TableCell>
                <TableCell sx={{ fontWeight: 600, textAlign: 'right' }} width={80}>Tokens</TableCell>
                <TableCell sx={{ fontWeight: 600 }} width={130}>Last Indexed</TableCell>
                <TableCell sx={{ fontWeight: 600 }} width={110}>Index Status</TableCell>
                <TableCell width={90} />
              </TableRow>
            </TableHead>

            <TableBody>
              {loading
                ? Array.from({ length: 5 }).map((_, i) => (
                    <TableRow key={i}>
                      {Array.from({ length: 9 }).map((__, j) => (
                        <TableCell key={j}>
                          <Skeleton variant="text" width={j === 0 ? 180 : 60} />
                        </TableCell>
                      ))}
                    </TableRow>
                  ))
                : items.length === 0
                ? (
                    <TableRow>
                      <TableCell colSpan={9} align="center" sx={{ py: 4 }}>
                        <Typography variant="body2" color="text.secondary">
                          No content items match the current filters.
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )
                : items.map(item => (
                    <TableRow key={item.contentItemId} hover>
                      <TableCell>
                        <Typography variant="body2" noWrap sx={{ maxWidth: 320 }}>
                          {item.title}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <TypeChip type={item.contentType} />
                      </TableCell>
                      <TableCell>
                        <StatusChip status={item.contentStatus} />
                      </TableCell>
                      <TableCell align="right">
                        <QualityBadge score={item.qualityScore} />
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="body2">
                          {item.indexed ? item.chunkCount : '—'}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="body2">
                          {item.indexed ? fmtTokens(item.totalTokens) : '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" color="text.secondary">
                          {fmtDate(item.lastIndexedAt)}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <IndexedChip indexed={item.indexed} />
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={0.5}>
                          <Tooltip title="Re-index">
                            <IconButton
                              size="small"
                              onClick={() => setPendingAction({
                                kind: 'reindex',
                                contentItemId: item.contentItemId,
                                title: item.title,
                              })}
                            >
                              <ReplayIcon sx={{ fontSize: 16 }} />
                            </IconButton>
                          </Tooltip>
                          {item.indexed && (
                            <Tooltip title="Delete index">
                              <IconButton
                                size="small"
                                color="error"
                                onClick={() => setPendingAction({
                                  kind: 'deleteIndex',
                                  contentItemId: item.contentItemId,
                                  title: item.title,
                                })}
                              >
                                <DeleteOutlineIcon sx={{ fontSize: 16 }} />
                              </IconButton>
                            </Tooltip>
                          )}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))
              }
            </TableBody>
          </Table>
        </TableContainer>

        <TablePagination
          component="div"
          count={total}
          page={page}
          rowsPerPage={rowsPerPage}
          rowsPerPageOptions={[10, 20, 50]}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={e => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
        />
      </Paper>

      <ConfirmDialog
        action={pendingAction}
        loading={actionLoading}
        onConfirm={handleConfirm}
        onCancel={() => setPendingAction(null)}
      />
    </Box>
  );
}
