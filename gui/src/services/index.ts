/**
 * Services Layer
 * 
 * Exports all business logic services.
 * 
 * Usage:
 * ```ts
 * import { authService, friendService } from '../services';
 * 
 * authService.logout();
 * const friends = friendService.getFriends();
 * ```
 */

// Auth state management
export { authService } from './authService';
export type { AuthService } from './authService';

// Admin auth state management
export { adminAuthService } from './adminAuthService';
export type { AdminAuthService, AdminUser } from './adminAuthService';