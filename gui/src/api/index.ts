/**
 * API Layer
 * 
 * Exports all API modules for backend communication.
 * 
 * Usage:
 * ```ts
 * import { authApi, userApi, httpClient } from '../api';
 * 
 * const user = await userApi.getCurrentUser(showError);
 * const tokens = await authApi.exchangeStateToken(state, showError);
 * ```
 */

// Base HTTP client
export { httpClient } from './httpClient';

// Domain-specific APIs
export { authApi } from './authApi';
export { userApi } from './userApi';
