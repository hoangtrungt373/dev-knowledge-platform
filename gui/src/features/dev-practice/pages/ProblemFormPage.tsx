import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
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
import { Difficulty, ParamType, ParsedSignature, Problem, ProblemStatus, ProblemTag } from '../types';
import { devPracticeApi } from '../api/devPracticeApi';
import { DIFFICULTIES, DIFFICULTY_LABEL, STATUSES, STATUS_LABEL } from '../constants';
import {
  contractFingerprint,
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
import MethodSignatureEditor, { SignatureTypeHints } from '../components/MethodSignatureEditor';
import CodeTemplateImporter from '../components/CodeTemplateImporter';
import TestCaseEditor from '../components/TestCaseEditor';
import ReferenceSolutionPanel from '../components/ReferenceSolutionPanel';
import { useNotification } from '@shared/contexts/NotificationContext';
import FullPageLoader from '@shared/components/FullPageLoader';
import SubmitButton from '@shared/components/SubmitButton';
import MarkdownField from '@shared/components/MarkdownField';
import TagPicker from '@shared/components/TagPicker';
import { useStagedTagPicker } from '@shared/hooks/useStagedTagPicker';

const LIST_PATH = '/admin/problems';
const editPath = (id: number) => `${LIST_PATH}/${id}/edit`;

function formatDateTime(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—';
}

/**
 * Create/edit page for one coding problem — `/admin/problems/new` and `/admin/problems/:id/edit`.
 * Main column: title, description, method signature, test cases. Sidebar: difficulty, status, a
 * tag chip picker, and (edit mode) read-only slug/dates. The whole problem, including every parameter and test case,
 * is sent in one create/update call — the backend replaces both lists wholesale.
 *
 * Publishing needs an ACCEPTED reference solution at the problem's current contract version
 * (`ReferenceSolutionPanel`), and a reference can only be judged against a *saved* problem — so a
 * new problem is always created as a Draft and the page then moves to its edit view, and saving an
 * existing problem stays on the page (re-seeded from the response) instead of returning to the list.
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
  // Same picker as @ecommerce's product form (existing tags + a "New tags" queue created on save).
  // The catalog is small (tens of topics), so it loads all of it once.
  const problemTags = useStagedTagPicker<ProblemTag>({
    loadTags: () => devPracticeApi.listAllProblemTags(showError),
    createTag: name => devPracticeApi.createProblemTag(name, showError),
  });
  const { selectedTagIds, setSelectedTagIds, resolveStagedTagIds, clearStagedTagNames } = problemTags;
  const [typeHints, setTypeHints] = useState<SignatureTypeHints>({ params: {} });

  // What the server last returned — the signature lock compares against these, not against
  // whatever the form currently shows.
  const [loaded, setLoaded] = useState<Problem | null>(null);
  const [originalSignature, setOriginalSignature] = useState<Signature | null>(null);

  const [errors, setErrors] = useState<ProblemFormErrors>(EMPTY_ERRORS);
  const [submitted, setSubmitted] = useState(false);
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);

  /** Seeds every field from a server response — on first load, and again after each save. */
  const applyProblem = (problem: Problem) => {
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
  };

  useEffect(() => {
    if (!isEdit || !id) return;
    // Also runs when a just-created problem's page switches from /new to /:id/edit.
    setLoading(true);
    devPracticeApi.getProblem(Number(id), showError)
      .then(applyProblem)
      .catch(() => navigate(LIST_PATH))
      .finally(() => setLoading(false));
    // applyProblem only calls state setters, which are stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, isEdit, showError, navigate]);

  // Mirrors ProblemServiceImpl's own rule: the lock only applies while the problem was PUBLISHED
  // and stays PUBLISHED through this save. Choosing Draft/Archived here unlocks it in the same call.
  const signatureLocked = loaded?.status === 'PUBLISHED' && status === 'PUBLISHED';

  const currentSignature = useMemo(
    () => signatureOf(methodName, returnType, params),
    [methodName, returnType, params],
  );

  // Does the form hold signature/test-data edits the server hasn't seen? A reference is judged
  // against the saved problem, so it can't verify those until they're saved.
  const savedContract = useMemo(
    () => (loaded && originalSignature ? contractFingerprint(originalSignature, loaded.testCases) : null),
    [loaded, originalSignature],
  );
  const contractDirty = savedContract !== null && savedContract !== contractFingerprint(currentSignature, testCases);

  // Mirrors ProblemServiceImpl's publish rule: PUBLISHED needs an ACCEPTED reference at the current
  // contract version. A problem that's already published may stay published as long as its contract
  // isn't touched (problems published before DKP-0056 stay live without one). Null = allowed.
  const publishBlockedReason = useMemo((): string | null => {
    if (!loaded) return 'Create the problem as a Draft first, then run a reference solution to publish it.';
    if (contractDirty) {
      return loaded.status === 'PUBLISHED'
        ? 'Changing the signature or test cases of a published problem needs re-verification: set the status to Draft, save, run a reference solution, then publish.'
        : 'Save the signature/test-case changes as a Draft and run a reference solution before publishing.';
    }
    if (loaded.status !== 'PUBLISHED' && !loaded.verified) {
      return 'Run a reference solution that passes every test case before publishing.';
    }
    return null;
  }, [loaded, contractDirty]);

  const runValidation = () =>
    validateProblemForm({
      title, description, methodName, params, testCases,
      signatureLocked, originalSignature, currentSignature,
      publishBlockedReason: status === 'PUBLISHED' ? publishBlockedReason : null,
    });

  // Before the first save attempt, errors only appear on submit; after it, they track every edit
  // live — so fixing a test case clears its error immediately instead of on the next click.
  useEffect(() => {
    if (submitted) setErrors(runValidation());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submitted, title, description, status, methodName, params, testCases, signatureLocked, currentSignature, publishBlockedReason]);

  /** Replaces the signature with what the template declared, remembering which types were guesses. */
  const applyParsedSignature = (parsed: ParsedSignature) => {
    const rows: ParamRow[] = parsed.parameters.map(p => ({ key: nextRowKey(), name: p.name, type: p.type }));
    const paramHints: Record<string, { chosen: ParamType; alternatives: ParamType[] }> = {};
    parsed.parameters.forEach((p, i) => {
      if (p.alternatives.length > 0) paramHints[rows[i].key] = { chosen: p.type, alternatives: p.alternatives };
    });
    setMethodName(parsed.methodName);
    setReturnType(parsed.returnType);
    setParams(rows);
    setTypeHints({
      returnType: parsed.returnTypeAlternatives.length > 0
        ? { chosen: parsed.returnType, alternatives: parsed.returnTypeAlternatives }
        : undefined,
      params: paramHints,
    });
    const guesses = Object.keys(paramHints).length + (parsed.returnTypeAlternatives.length > 0 ? 1 : 0);
    showSuccess(guesses === 0
      ? `Signature filled from the template: ${parsed.methodName}(${parsed.parameters.length} parameter(s))`
      : `Signature filled — ${guesses} type(s) were guessed, check the highlighted fields`);
  };

  /**
   * A reference was just ACCEPTED at the current contract version. A plain run only flips
   * `verified` locally. A "publish if accepted" run may have published the problem server-side
   * (in the same transaction as the verdict), so the saved problem is refetched to learn the real
   * status — but only `loaded` and the status field are updated, never the other form fields, so
   * unsaved edits (title, description, …) survive.
   */
  const handleVerified = async (publishRequested: boolean) => {
    const markVerified = () => setLoaded(prev => (prev ? { ...prev, verified: true } : prev));
    if (!publishRequested || !loaded) {
      markVerified();
      return;
    }
    try {
      const fresh = await devPracticeApi.getProblem(loaded.id, showError);
      setLoaded(fresh);
      // Otherwise the next Save would send the old status and silently unpublish it again.
      setStatus(fresh.status);
      if (fresh.status === 'PUBLISHED') showSuccess('Reference accepted — the problem is now published');
    } catch {
      markVerified();
    }
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
      // Queued new tag names are created now, right before the problem itself is saved — and only
      // after validation passed, so a form with errors never adds anything to the tag catalog.
      const newTagIds = await resolveStagedTagIds();
      clearStagedTagNames();
      // Always the complete tag set — never relies on the backend's "omitted = unchanged" update case.
      const payload = toPayload(
        { title, description, difficulty, status, methodName, returnType, tagIds: [...selectedTagIds, ...newTagIds] },
        params,
        testCases,
      );
      if (isEdit && id) {
        // Stay on the page: the usual next step after saving a draft is running a reference.
        applyProblem(await devPracticeApi.updateProblem(Number(id), payload, showError));
        setSubmitted(false);
        setErrors(EMPTY_ERRORS);
        showSuccess('Problem saved');
      } else {
        const created = await devPracticeApi.createProblem(payload, showError);
        showSuccess('Problem created — run a reference solution to be able to publish it');
        // /new and /:id/edit render the same component, so React keeps this instance: reset the
        // submit state here; the load effect re-seeds the fields once `id` changes.
        setSubmitted(false);
        setErrors(EMPTY_ERRORS);
        navigate(editPath(created.id), { replace: true });
      }
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

            <CodeTemplateImporter disabled={signatureLocked} onParsed={applyParsedSignature} />

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
              typeHints={typeHints}
            />

            <TestCaseEditor
              rows={testCases}
              onChange={setTestCases}
              params={params}
              returnType={returnType}
              errors={errors}
            />

            {loaded ? (
              <ReferenceSolutionPanel
                problem={loaded}
                contractDirty={contractDirty}
                onVerified={handleVerified}
              />
            ) : (
              <Alert severity="info">
                Create the problem as a Draft first — you can then run a reference solution here, which
                is required before it can be published.
              </Alert>
            )}
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
                {publishBlockedReason ? (
                  <Typography variant="caption" color={status === 'PUBLISHED' ? 'error' : 'text.secondary'}>
                    {publishBlockedReason}
                  </Typography>
                ) : (
                  <Typography variant="caption" color="text.secondary">
                    Only published problems are visible to users and accept submissions.
                  </Typography>
                )}
              </Stack>
            </Paper>

            {/* Tags — the same shared section as @ecommerce's ProductFormPage: an "Existing tags"
                Chip-toggle-cloud plus a "New tags" queue, created only when the problem itself is
                saved (see handleSubmit). Renaming/deleting a real tag still happens on
                /admin/problem-tags — this section is add-only. */}
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>Tags</Typography>
              <TagPicker
                picker={problemTags}
                stagedHint={`Created when you ${isEdit ? 'save' : 'create'} the problem.`}
              />
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
