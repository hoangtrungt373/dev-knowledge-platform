import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  FormControlLabel,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import {
  CategoryTreeNode,
  ContentStatus,
  CreateQuestionAnswerPayload,
  Difficulty,
  QuestionAnswer,
  Tag,
  UpdateQuestionAnswerPayload,
} from '../../types/admin.types';
import { adminApi } from '../../api/adminApi';
import { useNotification } from '../../contexts/NotificationContext';
import MarkdownField from '../../components/admin/MarkdownField';

interface FlatOption { id: number; name: string; depth: number; }

function flattenTree(nodes: CategoryTreeNode[], depth = 0): FlatOption[] {
  return nodes.flatMap(n => [
    { id: n.id, name: n.name, depth },
    ...flattenTree(n.children, depth + 1),
  ]);
}

const DIFFICULTIES: Difficulty[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];
const STATUSES: ContentStatus[] = ['DRAFT', 'PUBLISHED', 'ARCHIVED'];
const DIFFICULTY_LABEL: Record<Difficulty, string> = { BEGINNER: 'Beginner', INTERMEDIATE: 'Intermediate', ADVANCED: 'Advanced' };
const STATUS_LABEL: Record<ContentStatus, string> = { DRAFT: 'Draft', PUBLISHED: 'Published', ARCHIVED: 'Archived' };

export default function QuestionAnswerFormPage(): JSX.Element {
  const { id } = useParams<{ id: string }>();
  const isEdit = id !== undefined;
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();

  // Support data
  const [treeNodes, setTreeNodes] = useState<CategoryTreeNode[]>([]);
  const [allTags, setAllTags] = useState<Tag[]>([]);

  // Form state
  const [title, setTitle] = useState('');
  // Empty string means "not set" — difficulty is optional interview-specific metadata,
  // not every question (general dev-knowledge Q&A) needs one.
  const [difficulty, setDifficulty] = useState<Difficulty | ''>('');
  const [status, setStatus] = useState<ContentStatus>('DRAFT');
  const [categoryId, setCategoryId] = useState<number | ''>('');
  const [isCommon, setIsCommon] = useState(false);
  const [questionBody, setQuestionBody] = useState('');
  const [shortAnswer, setShortAnswer] = useState('');
  const [detailedAnswer, setDetailedAnswer] = useState('');
  const [selectedTagIds, setSelectedTagIds] = useState<Set<number>>(new Set());

  // UI state
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);

  const flatCategories = flattenTree(treeNodes);

  // Load support data + question (if editing)
  useEffect(() => {
    Promise.all([
      adminApi.getCategoryTree(showError),
      adminApi.listTags({ size: 1000, sortBy: 'name', sortDir: 'asc' }, showError),
    ]).then(([nodes, tagsPage]) => {
      setTreeNodes(nodes);
      setAllTags(tagsPage.content);
    });

    if (isEdit && id) {
      adminApi.getQuestionAnswer(Number(id), showError)
        .then((q: QuestionAnswer) => {
          setTitle(q.title);
          setDifficulty(q.difficulty ?? '');
          setStatus(q.status);
          setCategoryId(q.categoryId ?? '');
          setIsCommon(q.isCommon ?? false);
          setQuestionBody(q.questionBody);
          setShortAnswer(q.shortAnswer ?? '');
          setDetailedAnswer(q.detailedAnswer ?? '');
          setSelectedTagIds(new Set(q.tagIds));
        })
        .finally(() => setLoading(false));
    }
  }, [id, isEdit, showError]);

  const toggleTag = (tagId: number) => {
    setSelectedTagIds(prev => {
      const next = new Set(prev);
      next.has(tagId) ? next.delete(tagId) : next.add(tagId);
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
      const common = {
        title: title.trim(),
        difficulty: difficulty === '' ? null : difficulty,
        status,
        categoryId: categoryId === '' ? null : categoryId,
        isCommon,
        questionBody: questionBody.trim(),
        shortAnswer: shortAnswer.trim() || null,
        detailedAnswer: detailedAnswer.trim() || null,
        tagIds,
      };
      if (isEdit && id) {
        await adminApi.updateQuestionAnswer(Number(id), common as UpdateQuestionAnswerPayload, showError);
        showSuccess('Question updated');
      } else {
        await adminApi.createQuestionAnswer(common as CreateQuestionAnswerPayload, showError);
        showSuccess('Question created');
      }
      navigate('/admin/question-answers');
    } catch {
      // showError already called
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>

      {/* Header */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <IconButton
            size="small"
            onClick={() => navigate('/admin/question-answers')}
            title="Back to list"
          >
            <ArrowBackIcon fontSize="small" />
          </IconButton>
          <Typography variant="h5" fontWeight={700}>
            {isEdit ? 'Edit Question' : 'New Question'}
          </Typography>
        </Stack>
        <Stack direction="row" spacing={1}>
          <Button
            variant="outlined"
            onClick={() => navigate('/admin/question-answers')}
            disabled={saving}
          >
            Cancel
          </Button>
          <Button variant="contained" onClick={handleSubmit} disabled={saving}>
            {saving
              ? <CircularProgress size={16} color="inherit" />
              : isEdit ? 'Save' : 'Create'}
          </Button>
        </Stack>
      </Stack>

      {/* Two-column layout */}
      <Box sx={{ display: 'flex', gap: 3, alignItems: 'flex-start' }}>

        {/* ── Main content ── */}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Stack spacing={3}>

            <TextField
              label="Title"
              value={title}
              onChange={e => { setTitle(e.target.value); setErrors(p => ({ ...p, title: '' })); }}
              error={!!errors.title}
              helperText={errors.title || 'Slug is auto-generated from title'}
              fullWidth
              required
              autoFocus
              inputProps={{ maxLength: 500 }}
            />

            <Divider textAlign="left">
              <Typography variant="caption" color="text.secondary" fontWeight={600}>Question</Typography>
            </Divider>

            <MarkdownField
              label="Question Body"
              value={questionBody}
              onChange={v => { setQuestionBody(v); setErrors(p => ({ ...p, questionBody: '' })); }}
              required
              minRows={6}
              error={!!errors.questionBody}
              helperText={errors.questionBody}
              placeholder="Write the question here. Supports Markdown."
            />

            <Divider textAlign="left">
              <Typography variant="caption" color="text.secondary" fontWeight={600}>Answers</Typography>
            </Divider>

            <MarkdownField
              label="Short Answer"
              value={shortAnswer}
              onChange={setShortAnswer}
              minRows={4}
              placeholder="Brief answer — 1–3 sentences. Supports Markdown."
            />

            <MarkdownField
              label="Detailed Answer"
              value={detailedAnswer}
              onChange={setDetailedAnswer}
              minRows={10}
              placeholder="In-depth explanation with examples, code snippets, etc. Supports Markdown."
            />

          </Stack>
        </Box>

        {/* ── Sidebar ── */}
        <Box sx={{ width: 260, flexShrink: 0 }}>
          <Stack spacing={2}>

            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>
                Settings
              </Typography>
              <Stack spacing={1.5}>

                <FormControl fullWidth size="small">
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

                <FormControl fullWidth size="small">
                  <InputLabel>Difficulty</InputLabel>
                  <Select
                    label="Difficulty"
                    value={difficulty}
                    onChange={e => setDifficulty(e.target.value as Difficulty | '')}
                  >
                    <MenuItem value=""><em>Not set</em></MenuItem>
                    {DIFFICULTIES.map(d => (
                      <MenuItem key={d} value={d}>{DIFFICULTY_LABEL[d]}</MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <FormControl fullWidth size="small">
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

                <FormControlLabel
                  control={
                    <Switch
                      checked={isCommon}
                      onChange={e => setIsCommon(e.target.checked)}
                      size="small"
                    />
                  }
                  label={<Typography variant="body2">Mark as common (interview-prep)</Typography>}
                />

              </Stack>
            </Paper>

            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>
                Tags
              </Typography>
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
            </Paper>

          </Stack>
        </Box>

      </Box>
    </Box>
  );
}
