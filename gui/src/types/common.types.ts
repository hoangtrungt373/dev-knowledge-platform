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
