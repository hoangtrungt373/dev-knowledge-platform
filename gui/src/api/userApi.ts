import { User } from '../types';
import { httpClient } from './httpClient';

/**
 * User API Interface
 * 
 * Handles user-related API calls:
 * - Get current user
 * - Get user by ID
 * - Update user profile
 * - Search users
 */
interface UserApi {
  getCurrentUser(showError?: (message: string) => void): Promise<User>;
  getUserById(userUuid: string, showError?: (message: string) => void): Promise<User>;
  updateProfile(data: Partial<User>, showError?: (message: string) => void): Promise<User>;
  searchUsers(query: string, showError?: (message: string) => void): Promise<User[]>;
}

/**
 * User API Implementation
 */
export const userApi: UserApi = {
  /**
   * Get current authenticated user info
   * 
   * @param showError Optional error notification handler
   * @returns Current user data
   */
  getCurrentUser(showError?: (message: string) => void): Promise<User> {
    return httpClient.get<User>('/api/v1/auth/user', showError);
  },

  /**
   * Get user by UUID
   * 
   * @param userUuid User UUID
   * @param showError Optional error notification handler
   * @returns User data
   */
  getUserById(userUuid: string, showError?: (message: string) => void): Promise<User> {
    return httpClient.get<User>(`/api/v1/auth/user/${userUuid}`, showError);
  },

  /**
   * Update user profile
   * TODO: Implement when backend endpoint is ready
   */
  updateProfile(data: Partial<User>, showError?: (message: string) => void): Promise<User> {
    return httpClient.put<User>('/api/v1/users/me', data, showError);
  },

  /**
   * Search users by query
   * TODO: Implement when backend endpoint is ready
   */
  searchUsers(query: string, showError?: (message: string) => void): Promise<User[]> {
    return httpClient.get<User[]>(`/api/v1/users/search?q=${encodeURIComponent(query)}`, showError);
  },
};
