import { PagedResponse, User, UserSearchParams, UserSearchResult } from '../types';
import { httpClient } from './httpClient';

function buildQuery(params: Record<string, string | number | undefined>): string {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') q.set(k, String(v));
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}

interface UserApi {
  getCurrentUser(showError?: (message: string) => void): Promise<User>;
  getUserById(userUuid: string, showError?: (message: string) => void): Promise<User>;
  updateProfile(data: Partial<User>, showError?: (message: string) => void): Promise<User>;
  uploadAvatar(file: File, showError?: (message: string) => void): Promise<User>;
  searchUsers(params: UserSearchParams, showError?: (message: string) => void): Promise<PagedResponse<UserSearchResult>>;
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

  uploadAvatar(file: File, showError?: (message: string) => void): Promise<User> {
    const form = new FormData();
    form.append('file', file);
    return httpClient.postForm<User>('/api/v1/users/me/avatar', form, showError);
  },

  // Was previously typed as a flat User[] against a bare `?q=` query — didn't match the real
  // backend contract (paginated PagedResponse<UserSearchResultResponse>, which also carries the
  // viewer's relationshipStatus/mutualFriendCount for each result). Fixed as part of building the
  // friend-management UI, which is the first real caller of this endpoint.
  searchUsers(params: UserSearchParams, showError?: (message: string) => void): Promise<PagedResponse<UserSearchResult>> {
    return httpClient.get(
      `/api/v1/users/search${buildQuery(params as Record<string, string | number | undefined>)}`,
      showError,
    );
  },
};
