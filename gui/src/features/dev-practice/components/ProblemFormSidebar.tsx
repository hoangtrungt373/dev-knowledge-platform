import { ReactNode } from 'react';
import { Box, FormControl, InputLabel, MenuItem, Paper, Select, Stack, Typography } from '@mui/material';
import { Difficulty, Problem, ProblemStatus, ProblemTag } from '../types';
import { DIFFICULTIES, DIFFICULTY_LABEL, STATUSES, STATUS_LABEL } from '../constants';
import { formatDateTime } from '../utils/format';
import TagPicker from '@shared/components/TagPicker';
import { StagedTagPickerResult } from '@shared/hooks/useStagedTagPicker';

interface Props {
  difficulty: Difficulty;
  onDifficultyChange: (difficulty: Difficulty) => void;
  status: ProblemStatus;
  onStatusChange: (status: ProblemStatus) => void;
  /** Why Published can't be chosen right now, or null when it can — see `publishBlockedReason`. */
  publishBlockedReason: string | null;
  tagPicker: StagedTagPickerResult<ProblemTag>;
  isEdit: boolean;
  /** The saved problem (edit mode), for the read-only slug and dates. */
  loaded: Problem | null;
}

/** One titled, outlined sidebar card. */
function SidebarCard({ title, children }: { title: string; children: ReactNode }): JSX.Element {
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>{title}</Typography>
      {children}
    </Paper>
  );
}

function ReadOnlyField({ label, value, monospace }: { label: string; value: string; monospace?: boolean }): JSX.Element {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="body2" sx={monospace ? { fontFamily: 'monospace', wordBreak: 'break-all' } : undefined}>
        {value}
      </Typography>
    </Box>
  );
}

/**
 * The problem form's always-visible sidebar, outside the tabs: Settings (difficulty, status and why
 * Published may be blocked), Tags, and — once saved — the read-only slug and dates.
 */
export default function ProblemFormSidebar({
  difficulty,
  onDifficultyChange,
  status,
  onStatusChange,
  publishBlockedReason,
  tagPicker,
  isEdit,
  loaded,
}: Props): JSX.Element {
  return (
    <Stack spacing={2}>
      <SidebarCard title="Settings">
        <Stack spacing={1.5}>
          <FormControl fullWidth size="small">
            <InputLabel>Difficulty</InputLabel>
            <Select label="Difficulty" value={difficulty} onChange={e => onDifficultyChange(e.target.value as Difficulty)}>
              {DIFFICULTIES.map(d => <MenuItem key={d} value={d}>{DIFFICULTY_LABEL[d]}</MenuItem>)}
            </Select>
          </FormControl>
          <FormControl fullWidth size="small">
            <InputLabel>Status</InputLabel>
            <Select label="Status" value={status} onChange={e => onStatusChange(e.target.value as ProblemStatus)}>
              {STATUSES.map(s => (
                <MenuItem
                  key={s}
                  value={s}
                  // The current value stays selectable, so a published problem whose contract
                  // was just edited still shows its status (and the reason it can't stay).
                  disabled={s === 'PUBLISHED' && publishBlockedReason !== null && status !== 'PUBLISHED'}
                >
                  {STATUS_LABEL[s]}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          {/* Shown live, not only after a save attempt: red when it blocks the chosen status. */}
          <Typography
            variant="caption"
            color={publishBlockedReason && status === 'PUBLISHED' ? 'error' : 'text.secondary'}
          >
            {publishBlockedReason ?? 'Only published problems are visible to users and accept submissions.'}
          </Typography>
        </Stack>
      </SidebarCard>

      {/* The same shared section as @ecommerce's ProductFormPage: an "Existing tags" chip cloud plus a
          "New tags" queue, created only when the problem itself is saved. Renaming/deleting a real
          tag still happens on /admin/problem-tags — this section is add-only. */}
      <SidebarCard title="Tags">
        <TagPicker picker={tagPicker} stagedHint={`Created when you ${isEdit ? 'save' : 'create'} the problem.`} />
      </SidebarCard>

      {loaded && (
        <SidebarCard title="Details">
          <Stack spacing={1}>
            <ReadOnlyField label="Slug" value={loaded.slug} monospace />
            <ReadOnlyField label="Created" value={formatDateTime(loaded.createdAt)} />
            <ReadOnlyField label="Published" value={formatDateTime(loaded.publishedAt)} />
          </Stack>
        </SidebarCard>
      )}
    </Stack>
  );
}
