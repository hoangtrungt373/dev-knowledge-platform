export const STORAGE_KEYS = {
  accessToken: 'accessToken',
  refreshToken: 'refreshToken',
  userUuid: 'userUuid',
  username: 'username',
  email: 'email',
  role: 'role',
} as const;

// @tasks' manual drag-to-reorder order (see useTaskOrder) is per-view (per project id, or per
// smart filter), not a single fixed key, so it gets a prefix + builder instead of a STORAGE_KEYS
// entry — still routed through this file rather than hardcoded in the feature, per this file's own
// "one source of truth for localStorage keys" rule.
const TASK_ORDER_STORAGE_PREFIX = 'taskOrder:';
export const taskOrderStorageKey = (viewKey: string): string => `${TASK_ORDER_STORAGE_PREFIX}${viewKey}`;
