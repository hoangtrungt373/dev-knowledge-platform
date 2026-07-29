import { useEffect, useState } from 'react';
import { arrayMove } from '@dnd-kit/sortable';
import { taskOrderStorageKey } from '@shared/constants/storage';
import { Task } from '../types';

function readStoredOrder(viewKey: string): number[] {
  try {
    const raw = localStorage.getItem(taskOrderStorageKey(viewKey));
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((id): id is number => typeof id === 'number') : [];
  } catch {
    // Corrupted/manually-edited storage — fall back to "no saved order" rather than throwing.
    return [];
  }
}

function writeStoredOrder(viewKey: string, ids: number[]): void {
  localStorage.setItem(taskOrderStorageKey(viewKey), JSON.stringify(ids));
}

// Merges the previously-saved id order against the current fetch: stored ids that still exist
// keep their saved relative order, anything new (never dragged yet, or added since the last save)
// is appended at the end in its existing fetch order. The stored order is only ever advisory — a
// stored id no longer present (task deleted, or moved out of this filter) is silently dropped,
// never treated as an error or missing-data case.
function reconcile(storedIds: number[], tasks: Task[]): Task[] {
  const byId = new Map(tasks.map(t => [t.id, t]));
  const used = new Set<number>();
  const ordered: Task[] = [];
  for (const id of storedIds) {
    const task = byId.get(id);
    if (task) {
      ordered.push(task);
      used.add(id);
    }
  }
  for (const task of tasks) {
    if (!used.has(task.id)) ordered.push(task);
  }
  return ordered;
}

/**
 * Frontend-only manual task order for one "view" (a project filter, or a smart filter like
 * Today/This week) — persisted to localStorage, never sent to the backend (see docs/CHANGELOG.md's
 * `[Unreleased]` entry for why this was kept client-side). `viewKey` identifies the view
 * (`` `project:${id}` ``, `'today'`, `'week'`); pass `null` for views that don't support manual
 * ordering (the bucketed "All" dashboard, which stays auto-sorted by due date), in which case this
 * hook is a no-op passthrough of `tasks` in their given order.
 */
export function useTaskOrder(viewKey: string | null, tasks: Task[]) {
  const [orderedTasks, setOrderedTasks] = useState<Task[]>(tasks);

  useEffect(() => {
    if (!viewKey) {
      setOrderedTasks(tasks);
      return;
    }
    setOrderedTasks(reconcile(readStoredOrder(viewKey), tasks));
  }, [viewKey, tasks]);

  const reorder = (activeId: number, overId: number) => {
    if (!viewKey) return;
    setOrderedTasks(prev => {
      const oldIndex = prev.findIndex(t => t.id === activeId);
      const newIndex = prev.findIndex(t => t.id === overId);
      if (oldIndex === -1 || newIndex === -1 || oldIndex === newIndex) return prev;
      const next = arrayMove(prev, oldIndex, newIndex);
      writeStoredOrder(viewKey, next.map(t => t.id));
      return next;
    });
  };

  return { orderedTasks, reorder };
}
