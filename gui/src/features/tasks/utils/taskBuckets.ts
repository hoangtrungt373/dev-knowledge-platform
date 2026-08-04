import { Task } from '../types';

export type TaskBucketKey = 'OVERDUE' | 'TODAY' | 'UPCOMING' | 'COMPLETED';

export const BUCKET_ORDER: TaskBucketKey[] = ['OVERDUE', 'TODAY', 'UPCOMING', 'COMPLETED'];

export const BUCKET_LABEL: Record<TaskBucketKey, string> = {
  OVERDUE: 'Overdue',
  TODAY: 'Today',
  UPCOMING: 'Upcoming',
  COMPLETED: 'Completed',
};

/** Midnight local time, start of today. */
export function startOfToday(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

/** 23:59:59.999 local time, end of today — the inclusive upper bound `TaskSpecification` expects. */
export function endOfToday(): Date {
  const d = new Date();
  d.setHours(23, 59, 59, 999);
  return d;
}

/** 23:59:59.999 local time on the coming Sunday (today included if today is Sunday). */
export function endOfWeek(): Date {
  const d = new Date();
  const daysUntilSunday = (7 - d.getDay()) % 7;
  d.setDate(d.getDate() + daysUntilSunday);
  d.setHours(23, 59, 59, 999);
  return d;
}

export type DatePreset = 'TODAY' | 'TOMORROW' | 'THIS_WEEK';

export const DATE_PRESETS: DatePreset[] = ['TODAY', 'TOMORROW', 'THIS_WEEK'];

export const DATE_PRESET_LABEL: Record<DatePreset, string> = {
  TODAY: 'Today',
  TOMORROW: 'Tomorrow',
  THIS_WEEK: 'This week',
};

/** Local midnight for the given preset — matches how every due-date write in this feature
 * (DatePickerMenu, TaskQuickAdd, TaskRow, TaskDetailPanel) normalizes a date-only value before
 * sending it to the backend. Shared here (rather than duplicated per component) so "which day is
 * the coming Sunday" has exactly one implementation. */
export function datePresetValue(preset: DatePreset): Date {
  if (preset === 'TODAY') return startOfToday();
  if (preset === 'TOMORROW') {
    const d = startOfToday();
    d.setDate(d.getDate() + 1);
    return d;
  }
  const d = endOfWeek();
  d.setHours(0, 0, 0, 0);
  return d;
}

/** Today/Tomorrow/Yesterday, the weekday name for the rest of this week, else "Jul 30". Shared by
 * TaskRow's due-date chip and TaskQuickAdd's date-picker trigger so the label logic (and "which
 * day is the coming Sunday" boundary via endOfWeek()) has exactly one implementation. */
export function formatDueDateLabel(iso: string): string {
  const due = new Date(iso);
  const dueStart = new Date(due);
  dueStart.setHours(0, 0, 0, 0);

  const diffDays = Math.round((dueStart.getTime() - startOfToday().getTime()) / 86_400_000);
  if (diffDays === 0) return 'Today';
  if (diffDays === 1) return 'Tomorrow';
  if (diffDays === -1) return 'Yesterday';
  if (diffDays > 1 && dueStart.getTime() <= endOfWeek().getTime()) {
    return due.toLocaleDateString(undefined, { weekday: 'long' });
  }
  return due.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

/** True when the due date's calendar day is strictly before today — same "before today's start"
 * boundary bucketTasks() uses for its OVERDUE bucket, but callers still owe it their own
 * `status !== 'DONE'` check (a completed task's past due date isn't "overdue" for display purposes,
 * mirroring how bucketTasks() routes DONE tasks to COMPLETED regardless of due date). */
export function isOverdue(iso: string): boolean {
  const dueStart = new Date(iso);
  dueStart.setHours(0, 0, 0, 0);
  return dueStart.getTime() < startOfToday().getTime();
}

/**
 * Buckets tasks into Overdue/Today/Upcoming/Completed for the unfiltered dashboard view.
 * DONE tasks are always Completed regardless of due date. Tasks with no due date fall into
 * Upcoming (no due-date pressure to flag them as overdue or due today).
 */
export function bucketTasks(tasks: Task[]): Record<TaskBucketKey, Task[]> {
  const buckets: Record<TaskBucketKey, Task[]> = {
    OVERDUE: [], TODAY: [], UPCOMING: [], COMPLETED: [],
  };

  const todayStart = startOfToday().getTime();
  const todayEnd = endOfToday().getTime();

  for (const task of tasks) {
    if (task.status === 'DONE') {
      buckets.COMPLETED.push(task);
      continue;
    }
    if (!task.dueDate) {
      buckets.UPCOMING.push(task);
      continue;
    }
    const due = new Date(task.dueDate).getTime();
    if (due < todayStart) {
      buckets.OVERDUE.push(task);
    } else if (due <= todayEnd) {
      buckets.TODAY.push(task);
    } else {
      buckets.UPCOMING.push(task);
    }
  }

  return buckets;
}
