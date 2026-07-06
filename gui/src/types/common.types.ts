/**
 * Common Types
 * Shared types used across the application
 */

/**
 * Error response from backend API
 * Matches the ErrorResponse DTO from backend
 */
export interface ErrorResponse {
  errorCode: string;
  status: number;
  errorMessage: string;
  timestamp: string;
  path: string;
  validationErrors?: Record<string, string[]>;
  details?: Record<string, any>;
}

/**
 * Notification severity levels
 */
export type NotificationSeverity = 'success' | 'info' | 'warning' | 'error';

/**
 * Notification object for displaying alerts/toasts
 */
export interface Notification {
  id: string;
  message: string;
  severity: NotificationSeverity;
  duration?: number; // Auto-close duration in ms (default: 6000)
}

/**
 * Generic paginated response shape, matching the backend's PagedResponse<T> envelope.
 * Not admin-specific — used by every paginated endpoint (admin CRUD, friend graph, etc).
 */
export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
