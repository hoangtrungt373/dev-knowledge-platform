import { ErrorResponse } from '../types';

/**
 * Error Handler Utility
 * 
 * Distinguishes between business errors (4xx) and technical errors (5xx)
 * and provides appropriate error messages for users.
 */

const TECHNICAL_ERROR_MESSAGE = 'An unexpected error occurred. Please contact support if the problem persists.';
const SUPPORT_CONTACT = 'support@example.com';

/**
 * Check if error is a business error (4xx) or technical error (5xx)
 */
export function isBusinessError(status: number): boolean {
  return status >= 400 && status < 500;
}

/**
 * Check if error is a technical error (5xx)
 */
export function isTechnicalError(status: number): boolean {
  return status >= 500;
}

/**
 * Parse error response from backend
 */
export async function parseErrorResponse(response: Response): Promise<ErrorResponse | null> {
  try {
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      const data = await response.json();
      // Check if it matches ErrorResponse structure
      if (data.errorCode && data.status && data.errorMessage) {
        return data as ErrorResponse;
      }
    }
  } catch (error) {
    console.error('Failed to parse error response:', error);
  }
  return null;
}

/**
 * Get user-friendly error message
 * 
 * @param response HTTP response
 * @param errorResponse Parsed error response (optional)
 * @returns User-friendly error message
 */
export async function getUserFriendlyErrorMessage(
  response: Response,
  errorResponse?: ErrorResponse | null
): Promise<string> {
  // If errorResponse is not provided, try to parse it
  if (!errorResponse) {
    errorResponse = await parseErrorResponse(response);
  }

  // If we have a parsed error response
  if (errorResponse) {
    const status = errorResponse.status;

    // Business errors (4xx): Show actual error message
    if (isBusinessError(status)) {
      return errorResponse.errorMessage;
    }

    // Technical errors (5xx): Show generic message
    if (isTechnicalError(status)) {
      return TECHNICAL_ERROR_MESSAGE;
    }
  }

  // Fallback: Use status code to determine message
  if (isBusinessError(response.status)) {
    // Try to get error message from response
    try {
      const data = await response.json().catch(() => null);
      if (data?.errorMessage) {
        return data.errorMessage;
      }
      if (data?.error) {
        return data.error;
      }
    } catch {
      // Ignore parsing errors
    }

    // Default business error messages
    switch (response.status) {
      case 400:
        return 'Invalid request. Please check your input.';
      case 401:
        return 'Authentication required. Please log in.';
      case 403:
        return 'Access denied. You do not have permission.';
      case 404:
        return 'Resource not found.';
      case 409:
        return 'Resource already exists.';
      default:
        return 'Request failed. Please try again.';
    }
  }

  // Technical errors (5xx): Always show generic message
  if (isTechnicalError(response.status)) {
    return TECHNICAL_ERROR_MESSAGE;
  }

  // Unknown errors
  return 'An error occurred. Please try again.';
}

/**
 * Get error details for logging (not shown to user)
 */
export async function getErrorDetails(response: Response): Promise<string> {
  const errorResponse = await parseErrorResponse(response);
  
  if (errorResponse) {
    return `Error ${errorResponse.errorCode} (${errorResponse.status}): ${errorResponse.errorMessage} at ${errorResponse.path}`;
  }
  
  return `HTTP ${response.status} ${response.statusText} at ${response.url}`;
}

/**
 * Check if error requires user to contact support
 */
export function requiresSupportContact(status: number): boolean {
  return isTechnicalError(status);
}

/**
 * Get support contact information
 */
export function getSupportContact(): string {
  return SUPPORT_CONTACT;
}
