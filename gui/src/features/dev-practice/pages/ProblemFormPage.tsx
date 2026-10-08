import { ReactNode, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Box, Button, IconButton, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import VerifiedIcon from '@mui/icons-material/Verified';
import { Difficulty, ParamType, ParsedSignature, Problem, ProblemStatus, ProblemTag } from '../types';
import { devPracticeApi } from '../api/devPracticeApi';
import {
  contractFingerprint,
  EMPTY_ERRORS,
  firstTabWithErrors,
  formSnapshot,
  FormSnapshotFields,
  FORM_TABS,
  FormTab,
  hasErrors,
  nextRowKey,
  ParamRow,
  ProblemFormErrors,
  publishBlockedReason as publishBlockedReasonOf,
  rowsFromProblem,
  Signature,
  signatureFromTemplate,
  signatureOf,
  SignatureTypeHints,
  snapshotFieldsOf,
  tabsWithErrors,
  TestCaseRow,
  toPayload,
  validateProblemForm,
} from '../utils/problemForm';
import { useFormTab } from '../hooks/useFormTab';
import MethodSignatureEditor from '../components/MethodSignatureEditor';
import CodeTemplateImporter from '../components/CodeTemplateImporter';
import TestCaseEditor from '../components/TestCaseEditor';
import ReferenceSolutionPanel from '../components/ReferenceSolutionPanel';
import ProblemFormSidebar from '../components/ProblemFormSidebar';
import { useNotification } from '@shared/contexts/NotificationContext';
import FullPageLoader from '@shared/components/FullPageLoader';
import SubmitButton from '@shared/components/SubmitButton';
import MarkdownField from '@shared/components/MarkdownField';
import UnsavedChangesDialog from '@shared/components/UnsavedChangesDialog';
import { useUnsavedChangesGuard } from '@shared/hooks/useUnsavedChangesGuard';
import { useStagedTagPicker } from '@shared/hooks/useStagedTagPicker';

const LIST_PATH = '/admin/problems';
const editPath = (id: number) => `${LIST_PATH}/${id}/edit`;

/** What a brand-new form holds before any typing — the "clean" state in create mode. Must match the
 * `useState` initial values below, or a fresh /new page would already count as having changes. */
const NEW_PROBLEM_FIELDS: FormSnapshotFields = {
  title: '', description: '', difficulty: 'EASY', status: 'DRAFT', methodName: '', returnType: 'INT',
  parameters: [{ name: '', type: 'INT' }], testCases: [], tagIds: [], stagedTagNames: [],
};

const TAB_LABEL: Record<FormTab, string> = {
  details: 'Details',
  signature: 'Signature',
  testCases: 'Test cases',
  reference: 'Reference solution',
};

/** One tab's content. Hidden with `display: none`, never unmounted: the reference panel keeps
 * polling the judge and every CodeMirror editor keeps its text/undo history while another tab is open. */
function TabPanel({ active, children }: { active: boolean; children: ReactNode }): JSX.Element {
  return (
    <Box role="tabpanel" sx={{ display: active ? 'block' : 'none', pt: 3 }}>
      <Stack spacing={3}>{children}</Stack>
    </Box>
  );
}

/**
 * Create/edit page for one coding problem — `/admin/problems/new` and `/admin/problems/:id/edit`.
 * Main column: tabs for the details, method signature, test cases and reference solution. Sidebar
 * (`ProblemFormSidebar`): difficulty, status, tags, and (edit mode) read-only slug/dates. The whole
 * problem, including every parameter and test case, is sent in one create/update call — the backend
 * replaces both lists wholesale.
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
  const [activeTab, setActiveTab] = useFormTab();
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
  const { selectedTagIds, setSelectedTagIds, resolveStagedTagIds, clearStagedTagNames, stagedTagNames } = problemTags;
  const [typeHints, setTypeHints] = useState<SignatureTypeHints>({ params: {} });

  // What the server last returned — the signature lock compares against these, not against
  // whatever the form currently shows.
  const [loaded, setLoaded] = useState<Problem | null>(null);
  // The form's "clean" state for the unsaved-changes guard: the last loaded/saved problem, or the
  // empty defaults in create mode. Null while an edit page is still loading (nothing to lose yet).
  const [baseline, setBaseline] = useState<FormSnapshotFields | null>(() => (isEdit ? null : NEW_PROBLEM_FIELDS));
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
    setBaseline(snapshotFieldsOf(problem));
    setOriginalSignature(signatureOf(problem.methodName, problem.returnType, rows.params));
  };

  /** Back to "errors only appear on the next save attempt" — after a successful save. */
  const resetSubmitState = () => {
    setSubmitted(false);
    setErrors(EMPTY_ERRORS);
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
  const publishBlockedReason = publishBlockedReasonOf(loaded, contractDirty);

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
    const { params: rows, hints, guessCount } = signatureFromTemplate(parsed);
    setMethodName(parsed.methodName);
    setReturnType(parsed.returnType);
    setParams(rows);
    setTypeHints(hints);
    showSuccess(guessCount === 0
      ? `Signature filled from the template: ${parsed.methodName}(${parsed.parameters.length} parameter(s))`
      : `Signature filled — ${guessCount} type(s) were guessed, check the highlighted fields`);
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
      // The status changed server-side, not as an edit here — move the baseline with it, or the guard
      // would count the just-published status as an unsaved change.
      setBaseline(prev => (prev ? { ...prev, status: fresh.status } : prev));
      if (fresh.status === 'PUBLISHED') showSuccess('Reference accepted — the problem is now published');
    } catch {
      markVerified();
    }
  };

  const revertSignature = () => {
    if (!loaded) return;
    setMethodName(loaded.methodName);
    setReturnType(loaded.returnType);
    setParams(rowsFromProblem(loaded).params);
  };

  const handleSubmit = async () => {
    setSubmitted(true);
    const e = runValidation();
    setErrors(e);
    if (hasErrors(e)) {
      // Errors can sit in a tab that isn't open — take the admin to the first one. (A status-only
      // error lives in the always-visible sidebar, so there's no tab to switch to.)
      const errorTab = firstTabWithErrors(e);
      if (errorTab && !tabsWithErrors(e).has(activeTab)) setActiveTab(errorTab);
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
        resetSubmitState();
        showSuccess('Problem saved');
      } else {
        const created = await devPracticeApi.createProblem(payload, showError);
        showSuccess('Problem created — run a reference solution to be able to publish it');
        // /new and /:id/edit render the same component, so React keeps this instance: reset the
        // submit state here; the load effect re-seeds the fields once `id` changes.
        resetSubmitState();
        // Straight to the Reference tab: running a reference is the next step towards publishing.
        // The form still differs from the create-mode baseline until the edit page reloads it.
        allowNextNavigation();
        navigate(`${editPath(created.id)}?tab=reference`, { replace: true });
      }
    } catch {
      // showError already called
    } finally {
      setSaving(false);
    }
  };

  const isDirty = useMemo(() => baseline !== null && formSnapshot(baseline) !== formSnapshot({
    title, description, difficulty, status, methodName, returnType,
    parameters: params, testCases, tagIds: [...selectedTagIds], stagedTagNames,
  }), [baseline, title, description, difficulty, status, methodName, returnType, params, testCases, selectedTagIds, stagedTagNames]);
  const { blocker, allowNextNavigation } = useUnsavedChangesGuard(isDirty);

  // `errors` is only filled after the first save attempt, so tabs stay unflagged until then — the
  // same "errors appear on submit, then track live" rule as the fields themselves.
  const errorTabs = tabsWithErrors(errors);
  const referenceVerified = Boolean(loaded?.verified) && !contractDirty;

  const tabLabel = (tab: FormTab): string =>
    tab === 'testCases' ? `${TAB_LABEL[tab]} (${testCases.length})` : TAB_LABEL[tab];

  /** A red marker when the tab holds an error; the Reference tab shows its verified state instead. */
  const tabIcon = (tab: FormTab) => {
    if (errorTabs.has(tab)) return <ErrorOutlineIcon fontSize="small" color="error" />;
    if (tab === 'reference' && referenceVerified) return <VerifiedIcon fontSize="small" color="success" />;
    return undefined;
  };

  if (loading) {
    return <FullPageLoader />;
  }

  return (
    <Box sx={{ p: 3 }}>
      <UnsavedChangesDialog blocker={blocker} />
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <IconButton size="small" onClick={() => navigate(LIST_PATH)} title="Back to list">
            <ArrowBackIcon fontSize="small" />
          </IconButton>
          <Typography variant="h5" fontWeight={700}>{isEdit ? 'Edit Problem' : 'New Problem'}</Typography>
          {isDirty && <Typography variant="caption" color="warning.main">Unsaved changes</Typography>}
        </Stack>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => navigate(LIST_PATH)} disabled={saving}>Cancel</Button>
          <SubmitButton saving={saving} onClick={handleSubmit} label={isEdit ? 'Save' : 'Create'} />
        </Stack>
      </Stack>

      <Box sx={{ display: 'flex', gap: 3, alignItems: 'flex-start' }}>
        {/* ── Main content ── */}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
            <Tabs value={activeTab} onChange={(_, v: FormTab) => setActiveTab(v)} variant="scrollable">
              {FORM_TABS.map(tab => (
                <Tab
                  key={tab}
                  value={tab}
                  label={tabLabel(tab)}
                  icon={tabIcon(tab)}
                  iconPosition="end"
                  sx={{ minHeight: 48 }}
                />
              ))}
            </Tabs>
          </Box>

          <TabPanel active={activeTab === 'details'}>
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
          </TabPanel>

          <TabPanel active={activeTab === 'signature'}>
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
          </TabPanel>

          <TabPanel active={activeTab === 'testCases'}>
            <TestCaseEditor
              rows={testCases}
              onChange={setTestCases}
              params={params}
              returnType={returnType}
              errors={errors}
            />
          </TabPanel>

          <TabPanel active={activeTab === 'reference'}>
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
          </TabPanel>
        </Box>

        {/* ── Sidebar ── */}
        <Box sx={{ width: 260, flexShrink: 0 }}>
          <ProblemFormSidebar
            difficulty={difficulty}
            onDifficultyChange={setDifficulty}
            status={status}
            onStatusChange={setStatus}
            publishBlockedReason={publishBlockedReason}
            tagPicker={problemTags}
            isEdit={isEdit}
            loaded={loaded}
          />
        </Box>
      </Box>
    </Box>
  );
}
