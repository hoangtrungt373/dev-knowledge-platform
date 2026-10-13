import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  Collapse,
  IconButton,
  Paper,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import { AdminProblemSolution, AdminSolutionCode, Problem } from '../types';
import { LANGUAGE_LABEL, SUBMISSION_STATUS_LABEL } from '../constants';
import { devPracticeApi } from '../api/devPracticeApi';
import { submissionStats } from '../utils/format';
import ApproachFormDialog from './ApproachFormDialog';
import { useNotification } from '@shared/contexts/NotificationContext';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import MarkdownView from '@shared/components/MarkdownView';
import SectionStatus from '@shared/components/SectionStatus';

interface EditorialManagerProps {
  /** The saved problem — approaches link to its reference runs, and verification follows its contract version. */
  problem: Problem;
}

/**
 * The problem form's Editorial tab: the problem's solution approaches in display order, each saved
 * on its own (independent of the form's Save), like reference runs. Every approach shows which of its
 * linked runs learners currently see; a pending, rejected or stale one is flagged so it can be
 * replaced. "Preview" shows the explanation exactly as learners get it (placeholders expanded).
 * Reorder with the arrows; the server keeps positions 0..n-1.
 */
export default function EditorialManager({ problem }: EditorialManagerProps): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [approaches, setApproaches] = useState<AdminProblemSolution[] | null>(null);
  // null = closed; 'new' = adding; an approach = editing it.
  const [editing, setEditing] = useState<AdminProblemSolution | 'new' | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AdminProblemSolution | null>(null);
  const [busy, setBusy] = useState(false);
  const [previewIds, setPreviewIds] = useState<Set<number>>(new Set());

  const load = useCallback(() => {
    devPracticeApi.listSolutions(problem.id, showError)
      .then(setApproaches)
      .catch(() => setApproaches(prev => prev ?? []));
  }, [problem.id, showError]);

  // Reload when the saved test data changes: which links are "verified" depends on the contract version.
  useEffect(() => { load(); }, [load, problem.contractVersion]);

  const move = async (index: number, delta: -1 | 1) => {
    if (!approaches) return;
    const ids = approaches.map(a => a.id);
    [ids[index], ids[index + delta]] = [ids[index + delta], ids[index]];
    setBusy(true);
    try {
      setApproaches(await devPracticeApi.reorderSolutions(problem.id, ids, showError));
    } catch {
      load(); // likely a stale list — show the server's order
    } finally {
      setBusy(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    setBusy(true);
    try {
      await devPracticeApi.deleteSolution(problem.id, deleteTarget.id, showError);
      showSuccess('Approach deleted');
      setDeleteTarget(null);
      load();
    } catch {
      // showError already called; leave the dialog open to retry or cancel
    } finally {
      setBusy(false);
    }
  };

  const togglePreview = (id: number) => setPreviewIds(prev => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });

  return (
    <Stack spacing={2}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="body2" color="text.secondary">
          Approaches learners see in the Solution tab, in this order.
        </Typography>
        <Button variant="outlined" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
          Add approach
        </Button>
      </Stack>

      {approaches === null || approaches.length === 0 ? (
        <SectionStatus
          loading={approaches === null}
          isEmpty={approaches !== null}
          emptyMessage="No approaches yet — learners see no Solution content for this problem."
        />
      ) : (
        approaches.map((approach, index) => (
          <Paper key={approach.id} variant="outlined" sx={{ p: 2 }}>
            <Stack direction="row" alignItems="flex-start" spacing={1}>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography variant="subtitle1" fontWeight={700}>
                  {index + 1}. {approach.title}
                </Typography>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 0.75 }}>
                  {approach.codes.length === 0 ? (
                    <Chip size="small" label="No code linked" />
                  ) : approach.codes.map(code => <CodeChip key={code.submissionId} code={code} />)}
                </Stack>
              </Box>
              <Tooltip title="Preview as learners see it">
                <IconButton size="small" onClick={() => togglePreview(approach.id)}
                  color={previewIds.has(approach.id) ? 'primary' : 'default'}>
                  <VisibilityOutlinedIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <IconButton size="small" disabled={busy || index === 0} onClick={() => move(index, -1)} aria-label="Move up">
                <ArrowUpwardIcon fontSize="small" />
              </IconButton>
              <IconButton size="small" disabled={busy || index === approaches.length - 1}
                onClick={() => move(index, 1)} aria-label="Move down">
                <ArrowDownwardIcon fontSize="small" />
              </IconButton>
              <IconButton size="small" onClick={() => setEditing(approach)} aria-label="Edit">
                <EditIcon fontSize="small" />
              </IconButton>
              <IconButton size="small" color="error" onClick={() => setDeleteTarget(approach)} aria-label="Delete">
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Stack>
            <Collapse in={previewIds.has(approach.id)} unmountOnExit>
              <Box sx={{ mt: 2, pt: 2, borderTop: 1, borderColor: 'divider' }}>
                <MarkdownView content={approach.renderedExplanation} />
              </Box>
            </Collapse>
          </Paper>
        ))
      )}

      <ApproachFormDialog
        open={editing !== null}
        problem={problem}
        approach={editing === 'new' ? null : editing}
        onClose={() => setEditing(null)}
        onSaved={() => { setEditing(null); load(); }}
      />
      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete approach"
        message={`Delete "${deleteTarget?.title ?? ''}"? Its linked reference runs are kept.`}
        confirmLabel="Delete"
        loading={busy}
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </Stack>
  );
}

/** A linked run: green when learners see it, amber with the reason when they don't. */
function CodeChip({ code }: { code: AdminSolutionCode }): JSX.Element {
  const language = LANGUAGE_LABEL[code.language];
  if (code.verified) {
    const stats = submissionStats(code);
    return <Chip size="small" color="success" variant="outlined" label={stats ? `${language} · ${stats}` : language} />;
  }
  const reason = code.status === 'ACCEPTED'
    ? 'accepted against older test data'
    : SUBMISSION_STATUS_LABEL[code.status].toLowerCase();
  return (
    <Tooltip title={`Run #${code.submissionId} — learners see "being re-verified" until you link a fresh run`}>
      <Chip size="small" color="warning" variant="outlined" label={`${language} · ${reason}`} />
    </Tooltip>
  );
}
