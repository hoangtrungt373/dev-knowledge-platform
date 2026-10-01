import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { Difficulty, ParamType, Problem, ProblemStatus, ProblemTag } from '../types';
import { devPracticeApi } from '../api/devPracticeApi';
import { DIFFICULTIES, DIFFICULTY_LABEL, STATUSES, STATUS_LABEL } from '../constants';
import {
  EMPTY_ERRORS,
  hasErrors,
  nextRowKey,
  ParamRow,
  ProblemFormErrors,
  rowsFromProblem,
  Signature,
  signatureOf,
  TestCaseRow,
  toPayload,
  validateProblemForm,
} from '../utils/problemForm';
import MethodSignatureEditor from '../components/MethodSignatureEditor';
import TestCaseEditor from '../components/TestCaseEditor';
import { useNotification } from '@shared/contexts/NotificationContext';
import FullPageLoader from '@shared/components/FullPageLoader';
import SubmitButton from '@shared/components/SubmitButton';
import MarkdownField from '@shared/components/MarkdownField';

const LIST_PATH = '/admin/problems';

function formatDateTime(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—';
}

/**
 * Create/edit page for one coding problem — `/admin/problems/new` and `/admin/problems/:id/edit`.
 * Main column: title, description, method signature, test cases. Sidebar: difficulty, status, a
 * tag chip picker, and (edit mode) read-only slug/dates. The whole problem, including every parameter and test case,
 * is sent in one create/update call — the backend replaces both lists wholesale.
 */
export default function ProblemFormPage(): JSX.Element {
  const { id } = useParams<{ id: string }>();
  const isEdit = id !== undefined;
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [difficulty, setDifficulty] = useState<Difficulty>('EASY');
  const [status, setStatus] = useState<ProblemStatus>('DRAFT');
  const [methodName, setMethodName] = useState('');
  const [returnType, setReturnType] = useState<ParamType>('INT');
  const [params, setParams] = useState<ParamRow[]>(() => [{ key: nextRowKey(), name: '', type: 'INT' }]);
  const [testCases, setTestCases] = useState<TestCaseRow[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<Set<number>>(new Set());
  const [allTags, setAllTags] = useState<ProblemTag[]>([]);

  // What the server last returned — the signature lock compares against these, not against
  // whatever the form currently shows.
  const [loaded, setLoaded] = useState<Problem | null>(null);
  const [originalSignature, setOriginalSignature] = useState<Signature | null>(null);

  const [errors, setErrors] = useState<ProblemFormErrors>(EMPTY_ERRORS);
  const [submitted, setSubmitted] = useState(false);
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);

  // The whole catalog is small (tens of topics), so the picker just loads all of it once.
  useEffect(() => {
    devPracticeApi.listAllProblemTags(showError).then(setAllTags).catch(() => {});
  }, [showError]);

  useEffect(() => {
    if (!isEdit || !id) return;
    devPracticeApi.getProblem(Number(id), showError)
      .then(problem => {
        const rows = rowsFromProblem(problem);
        setTitle(problem.title);
        setDescription(problem.description);
        setDifficulty(problem.difficulty);
        setStatus(problem.status);
        setMethodName(problem.methodName);
        setReturnType(problem.returnType);
        setParams(rows.params);
        setTestCases(rows.testCases);
        setSelectedTagIds(new Set(problem.tags.map(t => t.id)));
        setLoaded(problem);
        setOriginalSignature(signatureOf(problem.methodName, problem.returnType, rows.params));
      })
      .catch(() => navigate(LIST_PATH))
      .finally(() => setLoading(false));
  }, [id, isEdit, showError, navigate]);

  // Mirrors ProblemServiceImpl's own rule: the lock only applies while the problem was PUBLISHED
  // and stays PUBLISHED through this save. Choosing Draft/Archived here unlocks it in the same call.
  const signatureLocked = loaded?.status === 'PUBLISHED' && status === 'PUBLISHED';

  const currentSignature = useMemo(
    () => signatureOf(methodName, returnType, params),
    [methodName, returnType, params],
  );

  const runValidation = () =>
    validateProblemForm({
      title, description, methodName, params, testCases,
      signatureLocked, originalSignature, currentSignature,
    });

  // Before the first save attempt, errors only appear on submit; after it, they track every edit
  // live — so fixing a test case clears its error immediately instead of on the next click.
  useEffect(() => {
    if (submitted) setErrors(runValidation());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submitted, title, description, methodName, params, testCases, signatureLocked, currentSignature]);

  const toggleTag = (tagId: number) => {
    setSelectedTagIds(prev => {
      const next = new Set(prev);
      if (next.has(tagId)) next.delete(tagId);
      else next.add(tagId);
      return next;
    });
  };

  const revertSignature = () => {
    if (!loaded) return;
    const rows = rowsFromProblem(loaded);
    setMethodName(loaded.methodName);
    setReturnType(loaded.returnType);
    setParams(rows.params);
  };

  const handleSubmit = async () => {
    setSubmitted(true);
    const e = runValidation();
    setErrors(e);
    if (hasErrors(e)) {
      showError('Please fix the highlighted fields');
      return;
    }
    setSaving(true);
    try {
      // Always the complete tag set — never relies on the backend's "omitted = unchanged" update case.
      const payload = toPayload(
        { title, description, difficulty, status, methodName, returnType, tagIds: [...selectedTagIds] },
        params,
        testCases,
      );
      if (isEdit && id) {
        await devPracticeApi.updateProblem(Number(id), payload, showError);
        showSuccess('Problem updated');
      } else {
        await devPracticeApi.createProblem(payload, showError);
        showSuccess('Problem created');
      }
      navigate(LIST_PATH);
    } catch {
      // showError already called
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <FullPageLoader />;
  }

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <IconButton size="small" onClick={() => navigate(LIST_PATH)} title="Back to list">
            <ArrowBackIcon fontSize="small" />
          </IconButton>
          <Typography variant="h5" fontWeight={700}>{isEdit ? 'Edit Problem' : 'New Problem'}</Typography>
        </Stack>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => navigate(LIST_PATH)} disabled={saving}>Cancel</Button>
          <SubmitButton saving={saving} onClick={handleSubmit} label={isEdit ? 'Save' : 'Create'} />
        </Stack>
      </Stack>

      <Box sx={{ display: 'flex', gap: 3, alignItems: 'flex-start' }}>
        {/* ── Main content ── */}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Stack spacing={3}>
            <TextField
              label="Title"
              value={title}
              onChange={e => setTitle(e.target.value)}
              error={Boolean(errors.title)}
              helperText={errors.title || 'Slug is auto-generated from the title'}
              fullWidth
              required
              autoFocus={!isEdit}
              inputProps={{ maxLength: 255 }}
            />

            <MarkdownField
              label="Description"
              value={description}
              onChange={setDescription}
              required
              minRows={8}
              error={Boolean(errors.description)}
              helperText={errors.description}
              placeholder="Describe the problem, constraints and examples. Supports Markdown."
            />

            <MethodSignatureEditor
              methodName={methodName}
              onMethodNameChange={setMethodName}
              returnType={returnType}
              onReturnTypeChange={setReturnType}
              params={params}
              onParamsChange={setParams}
              locked={signatureLocked}
              onRevert={isEdit ? revertSignature : undefined}
              errors={errors}
            />

            <TestCaseEditor
              rows={testCases}
              onChange={setTestCases}
              params={params}
              returnType={returnType}
              errors={errors}
            />
          </Stack>
        </Box>

        {/* ── Sidebar ── */}
        <Box sx={{ width: 260, flexShrink: 0 }}>
          <Stack spacing={2}>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>Settings</Typography>
              <Stack spacing={1.5}>
                <FormControl fullWidth size="small">
                  <InputLabel>Difficulty</InputLabel>
                  <Select
                    label="Difficulty"
                    value={difficulty}
                    onChange={e => setDifficulty(e.target.value as Difficulty)}
                  >
                    {DIFFICULTIES.map(d => <MenuItem key={d} value={d}>{DIFFICULTY_LABEL[d]}</MenuItem>)}
                  </Select>
                </FormControl>
                <FormControl fullWidth size="small">
                  <InputLabel>Status</InputLabel>
                  <Select
                    label="Status"
                    value={status}
                    onChange={e => setStatus(e.target.value as ProblemStatus)}
                  >
                    {STATUSES.map(s => <MenuItem key={s} value={s}>{STATUS_LABEL[s]}</MenuItem>)}
                  </Select>
                </FormControl>
                <Typography variant="caption" color="text.secondary">
                  Only published problems are visible to users and accept submissions.
                </Typography>
              </Stack>
            </Paper>

            <Paper variant="outlined" sx={{ p: 2 }}>
              <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1.5 }}>
                <Typography variant="subtitle2" fontWeight={700}>Tags</Typography>
                <Typography variant="caption" color="text.secondary">{selectedTagIds.size} selected</Typography>
              </Stack>
              {allTags.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  No tags yet — create them under Dev Practice → Problem Tags.
                </Typography>
              ) : (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
                  {allTags.map(tag => {
                    const selected = selectedTagIds.has(tag.id);
                    return (
                      <Chip
                        key={tag.id}
                        label={tag.name}
                        size="small"
                        clickable
                        color={selected ? 'primary' : 'default'}
                        variant={selected ? 'filled' : 'outlined'}
                        onClick={() => toggleTag(tag.id)}
                      />
                    );
                  })}
                </Box>
              )}
            </Paper>

            {loaded && (
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>Details</Typography>
                <Stack spacing={1}>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Slug</Typography>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
                      {loaded.slug}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Created</Typography>
                    <Typography variant="body2">{formatDateTime(loaded.createdAt)}</Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Published</Typography>
                    <Typography variant="body2">{formatDateTime(loaded.publishedAt)}</Typography>
                  </Box>
                </Stack>
              </Paper>
            )}
          </Stack>
        </Box>
      </Box>
    </Box>
  );
}
