import { User } from '../types';
import { httpClient } from '@shared/api/httpClient';

interface ProfileApi {
  getCurrentUser(showError?: (message: string) => void): Promise<User>;
  updateProfile(data: Partial<User>, showError?: (message: string) => void): Promise<User>;
  uploadAvatar(file: File, showError?: (message: string) => void): Promise<User>;
}

export const profileApi: ProfileApi = {
  getCurrentUser(showError?: (message: string) => void): Promise<User> {
    return httpClient.get<User>('/api/v1/auth/user', showError);
  },

  updateProfile(data: Partial<User>, showError?: (message: string) => void): Promise<User> {
    return httpClient.put<User>('/api/v1/users/me', data, showError);
  },

  uploadAvatar(file: File, showError?: (message: string) => void): Promise<User> {
    const form = new FormData();
    form.append('file', file);
    return httpClient.postForm<User>('/api/v1/users/me/avatar', form, showError);
  },
};
