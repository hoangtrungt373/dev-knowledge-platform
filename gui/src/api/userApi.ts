import { User } from '../types';
import { httpClient } from './httpClient';

interface UserApi {
  getCurrentUser(showError?: (message: string) => void): Promise<User>;
  getUserById(userUuid: string, showError?: (message: string) => void): Promise<User>;
  updateProfile(data: Partial<User>, showError?: (message: string) => void): Promise<User>;
  searchUsers(query: string, showError?: (message: string) => void): Promise<User[]>;
}

export const userApi: UserApi = {
  getCurrentUser(showError?: (message: string) => void): Promise<User> {
    return httpClient.get<User>('/api/v1/auth/user', showError);
  },

  // Fix 3: public profile is at /api/v1/users/public/{uuid}, not /api/v1/auth/user/{uuid}
  getUserById(userUuid: string, showError?: (message: string) => void): Promise<User> {
    return httpClient.get<User>(`/api/v1/users/public/${userUuid}`, showError);
  },

  updateProfile(data: Partial<User>, showError?: (message: string) => void): Promise<User> {
    return httpClient.put<User>('/api/v1/users/me', data, showError);
  },

  searchUsers(query: string, showError?: (message: string) => void): Promise<User[]> {
    return httpClient.get<User[]>(`/api/v1/users/search?q=${encodeURIComponent(query)}`, showError);
  },
};
