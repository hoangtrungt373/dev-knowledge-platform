import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
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
import { AdminProblemSolution, Problem, ProgrammingLanguage, Submission } from '../types';
import { LANGUAGES } from '../constants';
import { devPracticeApi } from '../api/devPracticeApi';
import { formatDateTime, submissionStats } from '../utils/format';
import { useNotification } from '@shared/contexts/NotificationContext';
import MarkdownField from '@shared/components/MarkdownField';
import SubmitButton from '@shared/components/SubmitButton';

interface ApproachFormDialogProps {
  open: boolean;
  problem: Problem;
  /** The approach being edited, or null to add a new one. */
  approach: AdminProblemSolution | null;
  onClose: () => void;
  /** Called after a successful save — the caller reloads its list. */
  onSaved: () => void;
}

/** language → chosen reference run id, or '' for "no implementation in this language". */
type RunChoice = Record<ProgrammingLanguage, number | ''>;

/** One picker entry; `run` is null for a linked run that isn't among the loaded recent runs. */
interface RunOption {
  run: Submission | null;
  id: number;
  /** Linked, but not an accepted run at the current contract version. */
  stale: boolean;
}

/** How many recent reference runs to offer — the admin's latest verified runs are always among them. */
const RUN_PAGE_SIZE = 100;

/**
 * Add or edit one editorial approach: a title, a Markdown explanation (prose only — code goes in as a
 * `{{code:LANGUAGE}}` placeholder), and, per language, which of the problem's ACCEPTED reference runs
 * at the current contract version backs it. Runs are picked, not typed: write and verify code in the
 * Reference solution tab first, then choose that run here — so every snippet learners see was judged.
 * The rules are enforced server-side; a violation comes back as the usual error toast and the dialog
 * stays open.
 */
export default function ApproachFormDialog({ open, problem, approach, onClose, onSaved }: ApproachFormDialogProps): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [title, setTitle] = useState('');
  const [explanation, setExplanation] = useState('');
  const [choice, setChoice] = useState<RunChoice>(emptyChoice());
  const [runs, setRuns] = useState<Submission[] | null>(null);
  const [saving, setSaving] = useState(false);
  const [triedSave, setTriedSave] = useState(false);

  // (Re)seed on every open: the admin may have just verified a new run in the Reference tab.
  useEffect(() => {
    if (!open) return;
    setTitle(approach?.title ?? '');
    setExplanation(approach?.explanation ?? '');
    const seeded = emptyChoice();
    approach?.codes.forEach(code => { seeded[code.language] = code.submissionId; });
    setChoice(seeded);
    setTriedSave(false);
    setRuns(null);
    let cancelled = false;
    devPracticeApi.listReferenceSubmissions(problem.id, RUN_PAGE_SIZE, showError)
      .then(page => { if (!cancelled) setRuns(page.content); })
      .catch(() => { if (!cancelled) setRuns([]); });
    return () => { cancelled = true; };
  }, [open, approach, problem.id, showError]);

  const titleError = triedSave && !title.trim();
  const explanationError = triedSave && !explanation.trim();

  /** Accepted runs at the current contract in `language`, newest first — plus the linked one if it's gone stale. */
  const optionsFor = (language: ProgrammingLanguage): RunOption[] => {
    const current = (runs ?? []).filter(r => r.language === language && r.status === 'ACCEPTED'
      && r.contractVersion === problem.contractVersion);
    const options: RunOption[] = current.map(run => ({ run, id: run.id, stale: false }));
    const linked = choice[language];
    if (linked !== '' && !options.some(o => o.id === linked)) {
      options.push({ run: (runs ?? []).find(r => r.id === linked) ?? null, id: linked, stale: true });
    }
    return options;
  };

  const insertPlaceholder = (language: ProgrammingLanguage) => {
    const placeholder = `{{code:${language}}}`;
    // Always its own paragraph — the server rejects a placeholder glued to other text.
    setExplanation(prev => (prev.trim() ? `${prev.replace(/\s+$/, '')}\n\n${placeholder}\n` : `${placeholder}\n`));
  };

  const handleSave = async () => {
    setTriedSave(true);
    if (!title.trim() || !explanation.trim()) return;
    const payload = {
      title: title.trim(),
      explanation,
      referenceSubmissionIds: LANGUAGES.map(l => choice[l.value]).filter((id): id is number => id !== ''),
    };
    setSaving(true);
    try {
      if (approach) {
        await devPracticeApi.updateSolution(problem.id, approach.id, payload, showError);
      } else {
        await devPracticeApi.createSolution(problem.id, payload, showError);
      }
      showSuccess(approach ? 'Approach saved' : 'Approach added');
      onSaved();
    } catch {
      // showError already called; keep the dialog open so nothing typed is lost
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="md">
      <DialogTitle>{approach ? 'Edit approach' : 'Add approach'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          <TextField
            label="Title"
            placeholder="e.g. Hash Set"
            value={title}
            onChange={e => setTitle(e.target.value)}
            required
            error={titleError}
            helperText={titleError ? 'Title is required' : undefined}
            inputProps={{ maxLength: 150 }}
          />

          <Box>
            <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 0.5 }}>Code</Typography>
            <Typography variant="caption" color="text.secondary" component="p" sx={{ mb: 1.5 }}>
              Pick an accepted reference run per language (verify new code in the Reference solution tab
              first). Only runs accepted at the current test data (version {problem.contractVersion}) are offered.
            </Typography>
            {runs === null ? (
              <Typography variant="body2" color="text.secondary">Loading reference runs…</Typography>
            ) : (
              <Stack spacing={1.5}>
                {LANGUAGES.map(({ value: language, label }) => {
                  const options = optionsFor(language);
                  return (
                    <Stack key={language} direction="row" spacing={1} alignItems="center">
                      <FormControl size="small" sx={{ flex: 1 }}>
                        <InputLabel>{label}</InputLabel>
                        <Select
                          label={label}
                          value={choice[language]}
                          onChange={e => setChoice(prev => ({ ...prev, [language]: e.target.value as number | '' }))}
                        >
                          <MenuItem value="">
                            <em>{options.length ? 'No implementation' : 'No accepted run yet'}</em>
                          </MenuItem>
                          {options.map(option => (
                            <MenuItem key={option.id} value={option.id}>
                              {runLabel(option.id, option.run)}
                              {option.stale && ' — not verified at the current version'}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                      <Button
                        size="small"
                        disabled={choice[language] === ''}
                        onClick={() => insertPlaceholder(language)}
                      >
                        Insert {`{{code:${language}}}`}
                      </Button>
                    </Stack>
                  );
                })}
              </Stack>
            )}
          </Box>

          <MarkdownField
            label="Explanation"
            value={explanation}
            onChange={setExplanation}
            required
            minRows={10}
            error={explanationError}
            helperText={explanationError
              ? 'Explanation is required'
              : 'Prose only — no code blocks. Put {{code:LANGUAGE}} on its own line where that language\'s verified code should appear.'}
          />
          {optionsHaveStale(choice, runs, problem.contractVersion) && (
            <Alert severity="warning">
              A linked run isn&apos;t accepted at the current test data, so learners see &quot;being
              re-verified&quot; instead of its code. Pick a fresh run to fix it.
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Cancel</Button>
        <SubmitButton saving={saving} onClick={handleSave} label={approach ? 'Save' : 'Add'} />
      </DialogActions>
    </Dialog>
  );
}

function emptyChoice(): RunChoice {
  return Object.fromEntries(LANGUAGES.map(l => [l.value, ''])) as RunChoice;
}

/** "#42 · Oct 13, 2026, 10:30 · Runtime 12 ms · Memory 40.2 MB" — or just the id if the run isn't in the page. */
function runLabel(id: number, run: Submission | null): string {
  if (!run) return `#${id}`;
  const stats = submissionStats(run);
  return [`#${id}`, formatDateTime(run.submittedAt), stats].filter(Boolean).join(' · ');
}

/** Whether any chosen run isn't an ACCEPTED one at the current contract (only knowable once runs load). */
function optionsHaveStale(choice: RunChoice, runs: Submission[] | null, contractVersion: number): boolean {
  if (!runs) return false;
  return Object.values(choice).some(id => {
    if (id === '') return false;
    const run = runs.find(r => r.id === id);
    return !run || run.status !== 'ACCEPTED' || run.contractVersion !== contractVersion;
  });
}
