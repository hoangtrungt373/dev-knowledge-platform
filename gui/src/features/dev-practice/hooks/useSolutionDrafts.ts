import { useEffect, useRef, useState } from 'react';
import { ProgrammingLanguage } from '../types';

type PerLanguage = Partial<Record<ProgrammingLanguage, string>>;

interface Options {
  /** Loads a language's starter code (the method stub for the problem's signature). */
  loadStarter: (language: ProgrammingLanguage) => Promise<string>;
  /**
   * Changes whenever the starter code could have changed (e.g. the problem's `contractVersion`):
   * cached starters are dropped and re-fetched, and an editor still showing the old untouched
   * starter is refreshed. Typed code is never replaced.
   */
  starterVersion: unknown;
  /** When set, drafts and the chosen language persist in `localStorage` under this key. */
  storageKey?: string;
  defaultLanguage?: ProgrammingLanguage;
}

export interface SolutionDrafts {
  language: ProgrammingLanguage;
  setLanguage: (language: ProgrammingLanguage) => void;
  /** The current language's draft ('' until its starter has loaded). */
  code: string;
  setCode: (code: string) => void;
  /** Puts another language's code in place, e.g. "load this past submission". */
  setCodeFor: (language: ProgrammingLanguage, code: string) => void;
  /** Replaces the current language's draft with its starter code. */
  resetToStarter: () => void;
  /** Whether the current draft differs from the starter (so a reset would lose something). */
  modified: boolean;
}

interface Stored {
  language?: ProgrammingLanguage;
  codes?: PerLanguage;
  /**
   * The starter each language's editor was last filled with. Persisted with the drafts so an untouched
   * draft from an earlier visit is still recognized as untouched — and refreshed — if the starter has
   * changed since (an admin edited the signature).
   */
  filledStarters?: PerLanguage;
}

function readStored(key: string | undefined): Stored {
  if (!key) return {};
  try {
    return JSON.parse(localStorage.getItem(key) ?? '{}') as Stored;
  } catch {
    return {}; // private mode, blocked storage or a corrupt value — start fresh
  }
}

/**
 * One code draft per language, pre-filled with the problem's starter code — the editor state behind
 * both the admin reference panel and the learner workspace. Switching language keeps every other
 * language's draft. Starters are fetched lazily, the first time a language is shown.
 *
 * "Untouched" is tracked as "still equal to the starter it was last filled with", so a new starter
 * (signature change) refreshes an editor nobody typed into, while real work is kept.
 */
export function useSolutionDrafts({
  loadStarter,
  starterVersion,
  storageKey,
  defaultLanguage = 'JAVA',
}: Options): SolutionDrafts {
  const [initial] = useState(() => readStored(storageKey));
  const [language, setLanguage] = useState<ProgrammingLanguage>(initial.language ?? defaultLanguage);
  const [codes, setCodes] = useState<PerLanguage>(initial.codes ?? {});
  // Starters fetched for the current starterVersion (cleared when it changes, which triggers a
  // re-fetch) vs. the starter each editor was last filled with (never cleared).
  const [starters, setStarters] = useState<PerLanguage>({});
  const lastFilledStarter = useRef<PerLanguage>(initial.filledStarters ?? {});
  const loadStarterRef = useRef(loadStarter);
  loadStarterRef.current = loadStarter;

  useEffect(() => {
    setStarters({});
  }, [starterVersion]);

  useEffect(() => {
    if (starters[language] !== undefined) return;
    let cancelled = false;
    loadStarterRef.current(language)
      .then(starter => {
        if (cancelled) return;
        const previous = lastFilledStarter.current[language];
        lastFilledStarter.current[language] = starter;
        setStarters(prev => ({ ...prev, [language]: starter }));
        // Only fill an editor nobody has typed into: empty, or still showing the old starter.
        setCodes(prev => (prev[language] === undefined || prev[language] === previous
          ? { ...prev, [language]: starter }
          : prev));
      })
      .catch(() => { /* leave the editor empty; a full solution can still be typed */ });
    return () => { cancelled = true; };
  }, [language, starters]);

  useEffect(() => {
    if (!storageKey) return;
    try {
      // lastFilledStarter is always updated in the same tick as the setCodes that re-runs this.
      const stored: Stored = { language, codes, filledStarters: lastFilledStarter.current };
      localStorage.setItem(storageKey, JSON.stringify(stored));
    } catch {
      // Storage full or blocked — the draft still lives in memory for this visit.
    }
  }, [storageKey, language, codes]);

  const code = codes[language] ?? '';
  const starter = starters[language];

  return {
    language,
    setLanguage,
    code,
    setCode: next => setCodes(prev => ({ ...prev, [language]: next })),
    setCodeFor: (lang, next) => setCodes(prev => ({ ...prev, [lang]: next })),
    resetToStarter: () => {
      if (starter !== undefined) setCodes(prev => ({ ...prev, [language]: starter }));
    },
    modified: starter !== undefined && code !== starter,
  };
}
