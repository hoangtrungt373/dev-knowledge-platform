import { useEffect, useState } from 'react';
import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { CreateTaskPayload, Project, Task, TaskPriority, UpdateTaskPayload } from '../types';
import { taskApi } from '../api/taskApi';
import { useNotification } from '@shared/contexts/NotificationContext';

interface Props {
  open: boolean;
  task: Task | null;
  /** Set only when creating a subtask via a row's "+ Add subtask" action — never user-editable. */
  parentTaskId?: number | null;
  projects: Project[];
  onClose: () => void;
  onSaved: () => void;
}

const PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

function toDateInputValue(iso: string | null): string {
  return iso ? iso.slice(0, 10) : '';
}

function toIsoOrNull(dateInput: string): string | null {
  return dateInput ? new Date(`${dateInput}T00:00:00`).toISOString() : null;
}

export default function TaskFormDialog({ open, task, parentTaskId, projects, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [projectId, setProjectId] = useState<number | ''>('');
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM');
  const [dueDate, setDueDate] = useState('');
  const [titleError, setTitleError] = useState('');
  const [saving, setSaving] = useState(false);

  const isEdit = task !== null;

  useEffect(() => {
    if (open) {
      setTitle(task?.title ?? '');
      setDescription(task?.description ?? '');
      setProjectId(task?.projectId ?? '');
      setPriority(task?.priority ?? 'MEDIUM');
      setDueDate(toDateInputValue(task?.dueDate ?? null));
      setTitleError('');
    }
  }, [open, task]);

  const validate = (): boolean => {
    if (!title.trim()) { setTitleError('Title is required'); return false; }
    if (title.trim().length > 255) { setTitleError('Title must not exceed 255 characters'); return false; }
    setTitleError('');
    return true;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSaving(true);
    try {
      const resolvedProjectId = projectId === '' ? null : projectId;
      if (isEdit) {
        const payload: UpdateTaskPayload = {
          title: title.trim(),
          description: description.trim() || null,
          projectId: resolvedProjectId,
          priority,
          dueDate: toIsoOrNull(dueDate),
          parentTaskId: task.parentTaskId,
        };
        await taskApi.updateTask(task.id, payload, showError);
        showSuccess('Task updated');
      } else {
        const payload: CreateTaskPayload = {
          title: title.trim(),
          description: description.trim() || undefined,
          projectId: resolvedProjectId ?? undefined,
          priority,
          dueDate: toIsoOrNull(dueDate) ?? undefined,
          parentTaskId: parentTaskId ?? undefined,
        };
        await taskApi.createTask(payload, showError);
        showSuccess(parentTaskId ? 'Subtask created' : 'Task created');
      }
      onSaved();
      onClose();
    } catch {
      // showError already called
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>
        {isEdit ? 'Edit Task' : parentTaskId ? 'New Subtask' : 'New Task'}
      </DialogTitle>

      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="Title"
            value={title}
            onChange={e => { setTitle(e.target.value); setTitleError(''); }}
            error={!!titleError}
            helperText={titleError}
            fullWidth
            autoFocus
            inputProps={{ maxLength: 255 }}
          />

          <TextField
            label="Description"
            value={description}
            onChange={e => setDescription(e.target.value)}
            fullWidth
            multiline
            minRows={2}
          />

          <FormControl fullWidth size="small">
            <InputLabel>Project</InputLabel>
            <Select
              label="Project"
              value={projectId}
              onChange={e => setProjectId(e.target.value as number | '')}
            >
              <MenuItem value=""><em>No project</em></MenuItem>
              {projects.map(p => (
                <MenuItem key={p.id} value={p.id}>{p.name}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl fullWidth size="small">
            <InputLabel>Priority</InputLabel>
            <Select
              label="Priority"
              value={priority}
              onChange={e => setPriority(e.target.value as TaskPriority)}
            >
              {PRIORITIES.map(p => (
                <MenuItem key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <TextField
            label="Due date"
            type="date"
            value={dueDate}
            onChange={e => setDueDate(e.target.value)}
            InputLabelProps={{ shrink: true }}
            fullWidth
          />

          {!isEdit && parentTaskId && (
            <Typography variant="caption" color="text.secondary">
              This will be created as a subtask.
            </Typography>
          )}
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Cancel</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={saving}>
          {saving ? <CircularProgress size={16} color="inherit" /> : (isEdit ? 'Save' : 'Create')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
