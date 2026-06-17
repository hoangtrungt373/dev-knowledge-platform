import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import {
  CategoryTreeNode,
  ContentStatus,
  CreateInterviewQuestionPayload,
  Difficulty,
  InterviewQuestion,
  Tag,
  UpdateInterviewQuestionPayload,
} from '../../types/admin.types';
import { adminApi } from '../../api/adminApi';
import { useNotification } from '../../contexts/NotificationContext';
import MarkdownField from './MarkdownField';

interface FlatOption { id: number; name: string; depth: number; }

function flattenTree(nodes: CategoryTreeNode[], depth = 0): FlatOption[] {
  return nodes.flatMap(n => [
    { id: n.id, name: n.name, depth },
    ...flattenTree(n.children, depth + 1),
  ]);
}

interface Props {
  open: boolean;
  question: InterviewQuestion | null;
  treeNodes: CategoryTreeNode[];
  allTags: Tag[];
  onClose: () => void;
  onSaved: () => void;
}

const DIFFICULTIES: Difficulty[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];
const STATUSES: ContentStatus[] = ['DRAFT', 'PUBLISHED', 'ARCHIVED'];

const DIFFICULTY_LABEL: Record<Difficulty, string> = {
  BEGINNER: 'Beginner',
  INTERMEDIATE: 'Intermediate',
  ADVANCED: 'Advanced',
};

const STATUS_LABEL: Record<ContentStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  ARCHIVED: 'Archived',
};

export default function InterviewQuestionFormDialog({
  open, question, treeNodes, allTags, onClose, onSaved,
}: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const isEdit = question !== null;

  // Metadata
  const [title, setTitle] = useState('');
  const [difficulty, setDifficulty] = useState<Difficulty>('INTERMEDIATE');
  const [status, setStatus] = useState<ContentStatus>('DRAFT');
  const [categoryId, setCategoryId] = useState<number | ''>('');
  const [isCommon, setIsCommon] = useState(false);

  // Content
  const [questionBody, setQuestionBody] = useState('');
  const [shortAnswer, setShortAnswer] = useState('');
  const [detailedAnswer, setDetailedAnswer] = useState('');

  // Tags
  const [selectedTagIds, setSelectedTagIds] = useState<Set<number>>(new Set());

  // UI
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  const flatCategories = flattenTree(treeNodes);

  useEffect(() => {
    if (open) {
      setTitle(question?.title ?? '');
      setDifficulty(question?.difficulty ?? 'INTERMEDIATE');
      setStatus(question?.status ?? 'DRAFT');
      setCategoryId(question?.categoryId ?? '');
      setIsCommon(question?.isCommon ?? false);
      setQuestionBody(question?.questionBody ?? '');
      setShortAnswer(question?.shortAnswer ?? '');
      setDetailedAnswer(question?.detailedAnswer ?? '');
      setSelectedTagIds(new Set(question?.tagIds ?? []));
      setErrors({});
    }
  }, [open, question]);

  const toggleTag = (id: number) => {
    setSelectedTagIds(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const validate = (): boolean => {
    const e: Record<string, string> = {};
    if (!title.trim()) e.title = 'Title is required';
    else if (title.trim().length > 500) e.title = 'Title must not exceed 500 characters';
    if (!questionBody.trim()) e.questionBody = 'Question body is required';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSaving(true);
    try {
      const tagIds = [...selectedTagIds];
      if (isEdit) {
        const payload: UpdateInterviewQuestionPayload = {
          title: title.trim(),
          difficulty,
          status,
          categoryId: categoryId === '' ? null : categoryId,
          isCommon,
          questionBody: questionBody.trim(),
          shortAnswer: shortAnswer.trim() || null,
          detailedAnswer: detailedAnswer.trim() || null,
          tagIds,
        };
        await adminApi.updateInterviewQuestion(question.id, payload, showError);
        showSuccess('Interview question updated');
      } else {
        const payload: CreateInterviewQuestionPayload = {
          title: title.trim(),
          difficulty,
          status,
          categoryId: categoryId === '' ? null : categoryId,
          isCommon,
          questionBody: questionBody.trim(),
          shortAnswer: shortAnswer.trim() || null,
          detailedAnswer: detailedAnswer.trim() || null,
          tagIds,
        };
        await adminApi.createInterviewQuestion(payload, showError);
        showSuccess('Interview question created');
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
    <Dialog
      open={open}
      onClose={saving ? undefined : onClose}
      maxWidth="md"
      fullWidth
      PaperProps={{ sx: { maxHeight: '92vh' } }}
    >
      <DialogTitle sx={{ pb: 1 }}>
        {isEdit ? 'Edit Interview Question' : 'New Interview Question'}
      </DialogTitle>

      <DialogContent dividers sx={{ py: 2 }}>
        <Stack spacing={2.5}>

          {/* Title */}
          <TextField
            label="Title"
            value={title}
            onChange={e => { setTitle(e.target.value); setErrors(p => ({ ...p, title: '' })); }}
            error={!!errors.title}
            helperText={errors.title || 'Slug is auto-generated from title'}
            fullWidth
            autoFocus
            required
            inputProps={{ maxLength: 500 }}
          />

          {/* Metadata row */}
          <Stack direction="row" spacing={2}>
            <FormControl size="small" sx={{ flex: 1 }}>
              <InputLabel>Category</InputLabel>
              <Select
                label="Category"
                value={categoryId}
                onChange={e => setCategoryId(e.target.value as number | '')}
              >
                <MenuItem value=""><em>No category</em></MenuItem>
                {flatCategories.map(opt => (
                  <MenuItem key={opt.id} value={opt.id}>
                    <span style={{ paddingLeft: opt.depth * 14 }}>{opt.name}</span>
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <FormControl size="small" sx={{ minWidth: 150 }}>
              <InputLabel>Difficulty</InputLabel>
              <Select
                label="Difficulty"
                value={difficulty}
                onChange={e => setDifficulty(e.target.value as Difficulty)}
              >
                {DIFFICULTIES.map(d => (
                  <MenuItem key={d} value={d}>{DIFFICULTY_LABEL[d]}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <FormControl size="small" sx={{ minWidth: 130 }}>
              <InputLabel>Status</InputLabel>
              <Select
                label="Status"
                value={status}
                onChange={e => setStatus(e.target.value as ContentStatus)}
              >
                {STATUSES.map(s => (
                  <MenuItem key={s} value={s}>{STATUS_LABEL[s]}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Stack>

          <FormControlLabel
            control={
              <Switch
                checked={isCommon}
                onChange={e => setIsCommon(e.target.checked)}
                size="small"
              />
            }
            label={
              <Typography variant="body2">
                Mark as common question
              </Typography>
            }
          />

          {/* Question body */}
          <Divider textAlign="left">
            <Typography variant="caption" color="text.secondary" fontWeight={600}>
              Question
            </Typography>
          </Divider>

          <MarkdownField
            label="Question Body"
            value={questionBody}
            onChange={v => { setQuestionBody(v); setErrors(p => ({ ...p, questionBody: '' })); }}
            required
            minRows={4}
            error={!!errors.questionBody}
            helperText={errors.questionBody}
            placeholder="Write the interview question here. Supports Markdown."
          />

          {/* Answers */}
          <Divider textAlign="left">
            <Typography variant="caption" color="text.secondary" fontWeight={600}>
              Answers
            </Typography>
          </Divider>

          <MarkdownField
            label="Short Answer"
            value={shortAnswer}
            onChange={setShortAnswer}
            minRows={3}
            placeholder="Brief answer — 1-3 sentences. Supports Markdown."
          />

          <MarkdownField
            label="Detailed Answer"
            value={detailedAnswer}
            onChange={setDetailedAnswer}
            minRows={6}
            placeholder="In-depth explanation with examples, code snippets, etc. Supports Markdown."
          />

          {/* Tags */}
          <Divider textAlign="left">
            <Typography variant="caption" color="text.secondary" fontWeight={600}>
              Tags
            </Typography>
          </Divider>

          {allTags.length === 0 ? (
            <Typography variant="body2" color="text.secondary">No tags available</Typography>
          ) : (
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
              {allTags.map(tag => {
                const selected = selectedTagIds.has(tag.id);
                return (
                  <Chip
                    key={tag.id}
                    label={tag.name}
                    size="small"
                    color={selected ? 'primary' : 'default'}
                    variant={selected ? 'filled' : 'outlined'}
                    onClick={() => toggleTag(tag.id)}
                    clickable
                  />
                );
              })}
            </Box>
          )}

        </Stack>
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 1.5 }}>
        <Button onClick={onClose} disabled={saving}>Cancel</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={saving}>
          {saving
            ? <CircularProgress size={16} color="inherit" />
            : isEdit ? 'Save' : 'Create'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
